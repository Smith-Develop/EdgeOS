# Avisos legales

## Licencia de EdgeOS

EdgeOS (EdgeOS Car y Vidrio) © 2026 Smith-Develop. Es software libre: puedes usarlo, estudiarlo, modificarlo y
repartirlo según la [GNU General Public License v3.0](LICENSE). Si repartes una versión modificada, tiene que ser
también GPL-3.0 y con su código fuente.

**Sin garantía.** Se ofrece tal cual, sin ninguna garantía (secciones 15 y 16 de la GPL-3.0). Desactivar apps del
sistema, cambiar el launcher o el brillo de la radio se hace bajo la responsabilidad de quien lo usa. Todo lo que
EdgeOS desactiva se puede devolver con *Restaurar todo*.

**Conduce con atención.** Configura la radio con el coche parado y respeta las normas de tráfico de tu país.

## Marcas

EdgeOS es un proyecto independiente. No está afiliado, patrocinado ni aprobado por Google, Apple, Motorola,
Ford, Reglink, MediaTek, Spotify, Waze ni ningún otro fabricante. Android y Android Auto son marcas de Google
LLC; iOS, iPhone y CarPlay, de Apple Inc. Los nombres de marcas, apps y coches se usan solo para decir con qué
es compatible EdgeOS. EdgeOS no incluye iconos, fuentes, fondos ni imágenes de esas marcas: los iconos y el
coche se dibujan con código propio.

## Privacidad

EdgeOS no tiene cuentas, anuncios ni analíticas, y no envía tus datos a nadie. La ubicación, los ajustes y tus
fotos se quedan en la radio o el móvil. Solo se conecta a internet para:

- las teselas del mapa de OpenStreetMap (`tile.openstreetmap.org`) cuando no hay mapas descargados;
- el catálogo y los mapas sin conexión de `download.mapsforge.org`, cuando tú los descargas;
- la lista de apps de UAD-ng en `raw.githubusercontent.com`, al abrir *Servicios innecesarios* o *Limpieza*;
- las páginas de PayPal o GitHub, solo si pulsas esos botones.

## Componentes de terceros

| Componente | Uso | Licencia |
|---|---|---|
| [osmdroid](https://github.com/osmdroid/osmdroid) 6.1.20 (con osmdroid-mapsforge) | Mapa de EdgeOS Car | Apache 2.0 |
| [Mapsforge](https://github.com/mapsforge/mapsforge) 0.21.0 | Dibujar los mapas sin conexión | LGPL-3.0 (se usa sin modificar; código en su repositorio) |
| [AndroidSVG](https://github.com/BigBadaboom/androidsvg) 1.4 | Iconos del mapa (lo trae Mapsforge) | Apache 2.0 |
| Kotlin, AndroidX, Jetpack Compose, Guava, JSpecify | Base de las apps | Apache 2.0 |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`backdrop`, `shapes`) | El vidrio de Vidrio | Apache 2.0 |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | Limpieza en Vidrio | MIT (texto abajo) |
| [Free My TV](https://github.com/simbasjungles/free-my-tv) | Cliente ADB de EdgeOS Car (`system/LocalAdb.kt`), adaptado | MIT (texto abajo) |
| [Inter](https://github.com/rsms/inter) | Fuente | SIL Open Font License 1.1 ([texto](LICENSE-Inter-OFL.txt)) |
| Datos de [OpenStreetMap](https://www.openstreetmap.org/copyright) | Mapas en línea y sin conexión | ODbL 1.0, © colaboradores de OpenStreetMap |
| Lista de [UAD-ng](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | Qué apps se pueden desactivar | GPL-3.0. Se descarga al usarla; no va dentro de las apps |

### Free My TV (MIT)

```
MIT License

Copyright (c) 2026 Free My TV contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Shizuku API (MIT)

```
MIT License

Copyright (c) 2021 RikkaW

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
