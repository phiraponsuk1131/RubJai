# RubJai 3.0.2

RubJai คือแอป Android สำหรับบันทึกรายรับ รายจ่าย สรุปการใช้เงิน และวางแผนปลดหนี้ พัฒนาด้วย Kotlin และ Jetpack Compose

- ชื่อผู้ช่วยในแอป: **น้องรับจ่าย**
- Package: `app.rubjai.mobile`
- minSdk 23 / targetSdk 35
- versionCode 21 / versionName 3.0.2

## การใช้งานหลัก

1. หน้าต้อนรับและแนะนำความสามารถของแอป
2. ยอมรับข้อตกลงและนโยบายจัดการข้อมูล
3. เลือกสิทธิ์เข้าถึงรูป โดยสามารถข้ามและอนุญาตภายหลังได้
4. สมัครหรือเข้าสู่ระบบด้วยเบอร์มือถือและ Firebase SMS OTP
   - มุมขวาบนของหน้าสมัคร/เข้าสู่ระบบแสดงเวอร์ชันและ version code ของ APK ที่กำลังใช้งาน
   - เลือกโหมดทดลองเพื่อเข้าใช้หน้าด้านในได้ก่อน โดยไม่ต้องรอ SMS OTP
   - ตรวจ GitHub Release และแสดง popup อัปเดตได้ตั้งแต่หน้าสมัคร/เข้าสู่ระบบ
5. หน้าหลักเป็นไทม์ไลน์โทนกรมท่า/เหลือง แสดงยอดเดือน แถบวัน รายการพร้อมไอคอนหมวด และปุ่ม `จดเพิ่ม`
6. เพิ่มรายรับ/รายจ่ายเป็นตัวเลข หรือเลือกสลิป K PLUS / Dime / ธนาคารที่มี QR เพื่ออ่านยอด ชื่อผู้รับ วันที่ และเวลา
7. ตรวจสอบรายการก่อนบันทึกด้วยหน้า editor ธีมเดียวกัน เลือกหมวดจาก bottom sheet พร้อมไอคอน แก้ไข และดูรูปสลิปต้นฉบับในเครื่องได้
8. สร้างหนี้หลายก้อน ใช้สลิปตัดยอด ดูประวัติและประมาณเดือนที่จะชำระหมด
9. รีเซ็ตเฉพาะข้อมูลของบัญชี หรือเลือกลบบัญชีถาวรแล้วสมัครใหม่ได้

## ความเป็นส่วนตัวและการกันข้อมูลซ้ำ

- ผู้ใช้เป็นคนเลือกสลิปหรือยินยอมให้แอปซิงค์รูปของวันนี้เอง
- รูปถูกอ่านชั่วคราวด้วย OCR บนเครื่อง ไม่อัปโหลด ไม่คัดลอก และไม่เก็บรูปใน Firebase
- รายการที่สแกนอัตโนมัติจะอยู่ในคิวบนเครื่องจนผู้ใช้อนุมัติหรือปฏิเสธ
- การซิงค์สลิปแสดงสถานะในหน้าหลักเท่านั้น ไม่เด้ง popup แจ้งผลสแกน
- การอ่านสลิปใช้ QR / mini-QR ก่อน แล้วใช้ OCR ช่วยเติมข้อมูลที่ QR ไม่ได้ให้มา
- Firestore เก็บเฉพาะยอด ชื่อ ประเภท หมวดที่ผู้ใช้เลือก โน้ต วันเวลา และรหัส SHA-256 สำหรับกันสลิปซ้ำ
- การเปิดดูสลิปใช้ลิงก์ภายในเครื่องไปยังรูปต้นฉบับ ไม่ส่งลิงก์หรือรูปไป Firebase หากรูปถูกลบหรือย้ายจะเปิดดูไม่ได้
- ข้อมูลอยู่ใต้ `users/{uid}` และกฎ Firestore อนุญาตให้เจ้าของบัญชีอ่านหรือแก้ข้อมูลของตัวเองเท่านั้น
- RubJai ไม่อ่านแชต LINE และไม่ใช้ Accessibility ดึงข้อความส่วนตัว

## ตั้งค่า Firebase

1. ใช้ Firebase project `rubjai-60e6d` และเพิ่ม Android app package `app.rubjai.mobile`
2. วาง `google-services.json` ที่ `app/google-services.json` เฉพาะในเครื่อง ไฟล์นี้ถูก ignore และห้าม commit
3. ไปที่ Authentication > Sign-in method แล้วเปิด **Phone**
4. เพิ่ม SHA-1 และ SHA-256 ของ signing certificate ใน Firebase Android app เพื่อให้ Phone Auth ทำงานกับ APK จริง
5. เปิด Cloud Firestore แล้ว deploy `firestore.rules`
6. เปิด App Check ด้วย Play Integrity ก่อนเผยแพร่สู่ผู้ใช้ทั่วไป
7. ตั้ง GitHub Secret `GOOGLE_SERVICES_JSON_BASE64` จากไฟล์ Firebase จริง
8. ตั้ง release signing secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

สร้างค่า Firebase secret ด้วย PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json"))
```

## โครงสร้าง Firestore

- `users/{uid}`: โปรไฟล์เจ้าของบัญชี
- `users/{uid}/transactions/{transactionId}`: รายรับและรายจ่าย
- `users/{uid}/debts/{debtId}`: หนี้แต่ละก้อน
- `users/{uid}/debts/{debtId}/payments/{slipFingerprint}`: ประวัติชำระที่กันสลิปซ้ำ

คอลเลกชันจะถูกสร้างเมื่อแอปบันทึกข้อมูลครั้งแรก ผู้ใช้ไม่ต้องสร้างเองใน Firebase Console

## Build และ Release

โปรเจกต์ใช้ JDK 17 และ Gradle 8.10.2:

```shell
gradle :app:assembleDebug
```

GitHub Actions บิลด์ APK ทุกครั้งที่ push โดยตรวจ text integrity, sample slip parsing และ redesigned UI flow ก่อน build แล้วแนบ APK กับ SHA-256 เป็น artifact เมื่อ push tag รูปแบบ `v*` จะสร้าง GitHub Release จาก `RELEASE_NOTES.md`

ระบบอัปเดตในแอปอ่านจาก [GitHub Releases](https://github.com/phiraponsuk1131/RubJai/releases/latest) และเปรียบเทียบ `versionName` แบบตัวเลข ดังนั้น release ต้องมี tag ที่ใหม่กว่าและมีไฟล์ APK แนบอยู่

