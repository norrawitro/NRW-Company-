# LOG — Android file finder

Android file finder : 2026-11-13 รับงานจาก Manager (ย้ายจาก general-project มา NRW-Company- ตามคำสั่ง Owner); ไม่เคยสร้างอะไรใน general-project จึงไม่มีอะไรต้องย้าย
Android file finder : 2026-11-13 เพิ่ม repo NRW-Company- เข้า session, สร้าง branch android-file-finder จาก main (มี PROJECT.md ของ Manager อยู่แล้ว, ไม่สร้างใหม่)
Android file finder : 2026-11-13 เขียนโค้ดแอป (Java, AGP 8.5.2, minSdk 21, targetSdk 34): ค้นชื่อไฟล์แบบ substring ไม่สนตัวพิมพ์, live search, แสดงชื่อ+path, แตะ=เปิด, กดค้าง=แชร์, ขอสิทธิ์ All files access ตอนเปิดครั้งแรก
Android file finder : 2026-11-13 เพิ่ม workflow .github/workflows/android-file-finder.yml (paths android-file-finder/** + manual run, sparse checkout เฉพาะโฟลเดอร์แอป ไม่อ่าน rooms/ ไม่ใช้ key ใด ๆ)
Android file finder : 2026-11-13 ใน cloud container ไม่มี Android SDK จึง build ผ่าน GitHub Actions เท่านั้น
Android file finder : 2026-11-13 CI run 1 ล้ม: ขั้น android-actions/setup-android@v3 — sdkmanager exit code 1 ("Failed to find package 'tools'"). แก้: ตัดขั้นนี้ออก ใช้ Android SDK ที่ติดตั้งมากับ ubuntu-latest ($ANDROID_HOME) แทน
Android file finder : 2026-11-13 CI run #4 (57dedbc) ผ่าน (green); artifact file-finder-debug-apk (~1.9 MB): https://github.com/norrawitro/NRW-Company-/actions/runs/43380776658/artifacts/14016478015
