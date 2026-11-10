package io.edgeos.car.system

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Cliente ADB que habla con la propia radio (127.0.0.1:5555, abierto cuando el ADB por red está activo): así
 * EdgeOS Car desactiva servicios sin Shizuku ni ordenador. La primera vez la radio pregunta "¿Permitir depuración
 * USB?" y se acepta con "Permitir siempre". Solo lo usa Optimizer, con sus propios comandos fijos.
 * Adaptado de Free My TV (github.com/simbasjungles/free-my-tv, MIT). Protocolo:
 * https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt
 */
class LocalAdb private constructor(private val socket: Socket) : Closeable {
    private val input = DataInputStream(socket.getInputStream())
    private val output: OutputStream = socket.getOutputStream()
    private var nextLocalId = 1

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Ejecuta un comando en la shell de la radio y devuelve su salida (stdout y stderr juntas). */
    @Synchronized
    fun shell(command: String): String {
        val local = nextLocalId++
        send(OPEN, local, 0, "shell:$command\u0000".toByteArray())
        var remote = 0
        val result = ByteArrayOutputStream()
        while (true) {
            val m = read()
            if (m.arg1 != local) continue
            when (m.command) {
                OKAY -> remote = m.arg0
                WRTE -> {
                    result.write(m.data)
                    send(OKAY, local, m.arg0, null)
                }
                CLSE -> {
                    if (remote == 0 && m.arg0 == 0) throw IOException("el ADB de la radio rechazó el comando")
                    send(CLSE, local, m.arg0, null)
                    return result.toString("UTF-8")
                }
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun read(): Message {
        val header = ByteArray(24)
        input.readFully(header)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        b.int // suma de control (el adbd moderno no la comprueba)
        if (command xor -1 != b.int) throw IOException("mensaje ADB no válido")
        if (length !in 0..1024 * 1024) throw IOException("mensaje ADB demasiado grande")
        val data = ByteArray(length)
        input.readFully(data)
        return Message(command, arg0, arg1, data)
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray?) {
        val length = data?.size ?: 0
        var sum = 0
        data?.forEach { sum += it.toInt() and 0xff }
        val b = ByteBuffer.allocate(24 + length).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(command xor -1)
        if (data != null) b.put(data)
        output.write(b.array())
        output.flush()
    }

    class Closed(message: String) : IOException(message)
    class NotApproved : IOException("no se aceptó el permiso en la pantalla de la radio")

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val STLS = 0x534c5453
        private const val VERSION = 0x01000001
        private const val MAX_DATA = 256 * 1024
        private const val TOKEN = 1
        private const val SIGNATURE = 2
        private const val PUBLIC_KEY = 3
        private val SHA1_PREFIX = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        /** ¿Está el ADB por red escuchando? Rápido y sin pedir nada en pantalla. */
        fun listening(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 5555), 400) }
        }.isSuccess

        /**
         * Conecta y se autentica. Si la radio aún no conoce la clave, llama a [waiting] (está mostrando el aviso)
         * y espera hasta [approvalMs] a que se acepte.
         */
        fun connect(context: Context, approvalMs: Int, waiting: () -> Unit): LocalAdb {
            val keys = keys(context)
            val s = Socket()
            try {
                s.connect(InetSocketAddress("127.0.0.1", 5555), 2000)
            } catch (e: IOException) {
                s.close()
                throw Closed("el ADB por red de la radio está apagado (puerto 5555 cerrado)")
            }
            val adb = LocalAdb(s)
            try {
                s.soTimeout = 8000
                adb.send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
                var signed = false
                var offered = false
                while (true) {
                    val m = try {
                        adb.read()
                    } catch (e: SocketTimeoutException) {
                        throw if (offered) NotApproved() else IOException("el ADB de la radio no responde")
                    }
                    when {
                        m.command == CNXN -> {
                            s.soTimeout = 30_000
                            return adb
                        }
                        m.command == STLS -> throw IOException("la radio pide ADB con TLS; usa el ADB por red clásico")
                        m.command != AUTH || m.arg0 != TOKEN -> throw IOException("respuesta inesperada del ADB")
                        !signed -> {
                            adb.send(AUTH, SIGNATURE, 0, sign(keys.private, m.data))
                            signed = true
                        }
                        !offered -> {
                            // Clave desconocida: se envía la pública y la radio muestra "¿Permitir depuración USB?".
                            adb.send(AUTH, PUBLIC_KEY, 0, publicKeyBlob(keys.public))
                            offered = true
                            waiting()
                            s.soTimeout = approvalMs
                        }
                        else -> throw IOException("la radio rechazó la clave de EdgeOS Car")
                    }
                }
            } catch (e: Exception) {
                adb.close()
                throw e
            }
        }

        // La clave de la app, en su almacenamiento privado.
        @Synchronized
        private fun keys(context: Context): KeyPair {
            val priv = File(context.filesDir, "adbkey")
            val pub = File(context.filesDir, "adbkey.pub")
            val factory = KeyFactory.getInstance("RSA")
            if (priv.exists() && pub.exists()) runCatching {
                return KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())),
                )
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            write(priv, pair.private.encoded)
            write(pub, pair.public.encoded)
            return pair
        }

        // Todo o nada: se escribe al lado, se sincroniza y solo entonces se renombra (la radio se apaga con el contacto).
        private fun write(f: File, data: ByteArray) {
            val tmp = File(f.path + ".tmp")
            tmp.outputStream().use { it.write(data); it.fd.sync() }
            if (!tmp.renameTo(f)) throw IOException("no se pudo guardar ${f.name}")
        }

        private fun sign(key: PrivateKey, token: ByteArray): ByteArray {
            val block = ByteArray(256)
            block[1] = 1
            val separator = block.size - SHA1_PREFIX.size - token.size - 1
            for (i in 2 until separator) block[i] = 0xff.toByte()
            System.arraycopy(SHA1_PREFIX, 0, block, separator + 1, SHA1_PREFIX.size)
            System.arraycopy(token, 0, block, separator + 1 + SHA1_PREFIX.size, token.size)
            return Cipher.getInstance("RSA/ECB/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key)
                doFinal(block)
            }
        }

        /** Clave pública en el formato de adbd (struct RSAPublicKey en base64, más un nombre). */
        private fun publicKeyBlob(key: PublicKey): ByteArray {
            val rsa = key as RSAPublicKey
            val n = rsa.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
            val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
            val b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(64)
            b.putInt(n0inv.toInt())
            b.put(littleEndian(n, 256))
            b.put(littleEndian(rr, 256))
            b.putInt(rsa.publicExponent.toInt())
            return (Base64.encodeToString(b.array(), Base64.NO_WRAP) + " edgeos-car@radio\u0000").toByteArray(Charsets.US_ASCII)
        }

        private fun littleEndian(value: BigInteger, size: Int): ByteArray {
            val big = value.toByteArray()
            val little = ByteArray(size)
            for (i in 0 until minOf(size, big.size)) little[i] = big[big.size - 1 - i]
            return little
        }
    }
}
