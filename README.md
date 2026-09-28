# DocuEditEngine ???

An advanced, enterprise-grade Android application that enables users to edit text in PDFs and Scanned Documents with **99% visual accuracy**.

## Core Capabilities
- **Google ML Kit Text v2 OCR**: Sub-pixel bounding box, stroke weight, rotation angle, and Otsu foreground ink-color sampling.
- **OpenCV Fast Marching Inpainting (Telea / Navier-Stokes)**: Erases original text while reconstructing paper grain, gradients, and micro-stains with horizontal border shielding.
- **Precision Typography Engine**: Matches system & Google Fonts, auto-condenses letter-spacing (kerning) and glyph aspect ratio (`scaleX`) to prevent overflow.
- **Artifact & Noise Blending Engine**: Measures surrounding sensor noise variance $\sigma^2$, simulates lens Point-Spread-Function (PSF) blur, and injects matching Gaussian micro-grain.
- **Angled & Deskewed Text Processor**: Affine canonical warping to level tilted text before editing.
- **Multi-Page PDF Streaming**: Disk-backed caching holding at most 1 page in RAM at 300 DPI print quality.
- **Forensic Privacy Stripper**: Wipes EXIF metadata and PDFBox XMP edit history.
- **Auto-Update System**: In-app GitHub releases checker with direct APK background download & installer.

## Architecture
- 100% Kotlin + Jetpack Compose
- MVVM / UDF Architecture
- OpenCV for Android (v4.9.0)
- PDFBox Android & Android Native `PdfRenderer`
