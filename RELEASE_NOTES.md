# RubJai v3.0.5

- Improved QR slip detection by retrying with cropped, upscaled, and rotated image candidates when the QR is not detected from the full image.
- Added support for QR payloads that expose amount outside EMV tag `54`, including `amount`, `amt`, `total`, `THB`, `BAHT`, and Thai amount labels.
- Added safe recipient-name extraction from explicit QR fields such as `recipient`, `receiver`, `toName`, `merchant`, `name`, and Thai recipient labels.
- Rejects unsafe or reference-like QR name values so transaction titles do not become broken OCR-style text.
- Rescans previously skipped gallery images with a new QR parser key.
- Raises the one-month sync scan cap to 3000 images so busy galleries can surface more real slips.
- Keeps the app QR-only for slip sync and manual slip import; OCR is still not used for amount, date, time, or recipient names.
