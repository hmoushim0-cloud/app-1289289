# تطبيق فوري باي — مصمم و مسؤول سوشيال ميديا — (`fawri-pay-pos-cashier-1289289`)
> منظومة pos_cashier المتكاملة مع ربط ESC/POS Thermal Receipt Printer + Barcode Scanner

- **Project ID**: `#1289289`
- **Commercial Archetype**: `pos_cashier`
- **Bound Hardware Peripheral**: `ESC/POS Thermal Receipt Printer + Barcode Scanner`
- **Package Name**: `sa.factory.fawripayposcashier`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)


## [Sidekick Autonomous Update]

### Architecture & Point-of-Sale Hardware Enhancements
- **ZATCA Phase 2 Fatoora Engine (`ZatcaPhase2FatooraEngine.kt`)**:
  - Full Saudi Tax and Customs Authority (ZATCA) Phase 2 e-invoicing compliance for simplified B2C and standard B2B tax invoices.
  - 15-digit Saudi VAT registration validation (starting and ending with digit 3).
  - Multi-tier tax categorization: Standard 15% VAT (`STANDARD_15`), Zero-Rated supplies (`ZERO_RATED`), and Exempt supplies (`EXEMPT`).
  - Binary TLV (Tag-Length-Value) encoder for Tags 1 through 8 (Seller Name, VAT Number, ISO 8601 Timestamp, Total with VAT, Total VAT, SHA-256 Invoice Digest, ECDSA Signature, and ECDSA Public Key).
  - Base64 QR code encoding and canonical SHA-256 digest computation for invoice integrity validation.
  - Tag parsing round-trip engine for verifying external QR code scans.

- **ESC/POS Thermal Receipt Printer Driver (`EscPosThermalPrinterDriver.kt`)**:
  - Direct binary ESC/POS command pipeline: printer initialization (`ESC @`), alignment formatting (`ESC a`), bold text toggling (`ESC E`), double-size header modes (`GS !`), cash drawer kick pulses (`ESC p`), and partial/full paper cuts (`GS V`).
  - Native 2D QR Code generation command sequences (`GS ( k`) for fast thermal rasterization without host bitmap processing.
  - 80mm and 58mm standard receipt text formatting with dynamic column justification and itemized billing tables.
  - Barcode Scanner Ingestion Engine supporting GS1 EAN-13, Code 128, and QR codes.
  - Modulo 10 check digit verification with GS1 Saudi Arabia (`628`) prefix recognition.
  - Scanner buffer framer with CRLF delimiter stripping.

- **Hardware Driver Integration (`HardwareSensorDriver.kt`)**:
  - Bound directly to `EscPosThermalPrinterDriver` and `ZatcaPhase2FatooraEngine` for real-time cashier telemetry streaming.

- **Automated Verification Harness**:
  - Comprehensive unit test suite in `sa.factory.fawripayposcashier.FawriPayFeatureTest` covering 7 test scenarios (invoice totals calculation, multi-tax categories, ZATCA TLV encoding and Base64 output, TLV tag parsing round-trip, GS1 EAN-13 modulo 10 verification, barcode scanner buffer ingestion, and ESC/POS byte sequence generation).
  - 100% test pass rate verified on OpenJDK 17 and Kotlin compiler.

### Build and Verification Instructions
```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"
mkdir -p /tmp/iter28_bin
kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/iter28_bin \
  app/src/main/java/sa/factory/fawripayposcashier/zatca/ZatcaPhase2FatooraEngine.kt \
  app/src/main/java/sa/factory/fawripayposcashier/hardware/EscPosThermalPrinterDriver.kt \
  app/src/test/java/sa/factory/fawripayposcashier/FawriPayFeatureTest.kt
java -cp "/tmp/iter28_bin:$CP" org.junit.runner.JUnitCore sa.factory.fawripayposcashier.FawriPayFeatureTest
```
