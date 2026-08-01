# Programming Lab Integrity Suite (PLIS)

PLIS 1.1.0 is one Windows desktop application for both teachers and students. A teacher computer hosts the classroom, while student computers automatically discover it over wired Ethernet or Wi-Fi.

The product contains two classroom workflows:

- **LabLink** provides controlled class joining, live and versioned student code, teacher edits shown in red, compile/run events, capacity enforcement, and visible application activity telemetry.
- **ExamBeacon** provides timed programming exams with focus-loss, disconnect, fast-completion, and code-similarity review signals.

## Install and start

Run `PLIS-1.1.0.exe` on every teacher and student computer. It includes its own Java runtime; end users do not need Java or Maven.

On the teacher computer:

1. Open PLIS and select **Host as Teacher**.
2. On first use, create the owner teacher ID. The suggested initial password is `12345`; change it after signing in.
3. Open **Accounts & access** and create each student ID. A new student initially uses password `12345` and can change it later.
4. Approve each student's computer when its access request appears.
5. Create a LabLink class, choose the allowed students, set the maximum participant count, and select **Ethernet or Wi-Fi**, **Ethernet only**, or **Wi-Fi only**.

On a student computer:

1. Open the same PLIS application and select **Connect as Student**.
2. Select the teacher account automatically discovered on the local network. If multicast discovery is restricted by the network, enter one of the manual addresses displayed at the top of the teacher window.
3. Enter the ID and initial password supplied by the teacher.
4. The first connection waits until the teacher approves that computer. After approval, PLIS reconnects automatically after normal interruptions. A teacher can terminate a device or all devices belonging to a student at any time.

For an exam, the student must choose Java, Python, C, C++, JavaScript, C#, or Kotlin before the editor unlocks. PLIS creates the appropriate filename and extension, saves live revisions while the student types, and locks the final source after submission. Safe in-application compile checking remains Java-only; other languages are preserved and exported without executing untrusted code.

Allow PLIS through Windows Defender Firewall on **Private networks** when Windows asks. Automatic discovery uses UDP multicast port `45454`; the classroom server uses the dynamic TCP port displayed in the teacher window. Both computers must be on a mutually reachable local network.

## Teacher administration

Only the teacher owner can:

- create, disable, re-enable, reset, and terminate student accounts;
- create hundreds of student accounts in one validated bulk paste operation;
- approve or terminate individual student computers;
- set LabLink participant limits and permitted connection type;
- watch live student code and send red teacher-authored revisions during active class time;
- review immutable code revisions, class events, login history, device approvals, terminations, and account changes;
- view live exam code, retain final submissions, and export selected or all students using their chosen language extension;
- review visible browser and native AI application open/close telemetry recorded during an active class;
- change the teacher password.

Bulk account rows accept comma-separated values or columns pasted directly from a spreadsheet in this order: `login ID, full name, student number, program, semester, password`. Semester defaults to `1` and password defaults to `12345` when omitted.

Passwords are stored as BCrypt hashes. Student access tokens are bound to an approved installation identity, and terminating a device immediately invalidates its active access. The teacher's database, signing secret, logs, and settings are stored under `%LOCALAPPDATA%\PLIS`.

## Monitoring and privacy boundary

Monitoring is disclosed in the student interface and operates only within PLIS classroom activity. The application records PLIS actions, code revisions, focus/network events, and the process names of supported browser or native AI applications while a student is joined to an active class.

PLIS does not install a keylogger, read passwords, capture the screen, inspect encrypted HTTPS content, or extract exact Google searches and browser-based AI prompts. Exact managed-browser URL/search reporting would require a separately consented browser extension or institutional proxy with an approved retention and privacy policy; it is intentionally outside this desktop release.

Compile checks do not execute untrusted student bytecode. Integrity flags are review signals for the teacher, not automatic findings of misconduct.

## Build the release from source

Development requires JDK 17 or newer, PowerShell, and internet access for the first dependency download. Maven is provided by the checked-in wrapper.

```powershell
cd C:\Users\syada\Documents\PLIS
.\mvnw.cmd test
powershell -ExecutionPolicy Bypass -File .\package-windows.ps1
```

The packaging script builds and verifies all modules, downloads the official pinned WiX 3.14.1 portable toolset when necessary, verifies its SHA-256 checksum, creates a bundled Java runtime, and writes:

- `release\installer\PLIS-1.1.0.exe` - the single distributable Windows installer;
- `release\app-image\PLIS\PLIS.exe` - the unpacked application used for local smoke testing.

## Ownership and licensing

PLIS is created and owned by **Syad Mehedi Hasan Alvi** (`alvi164`), Backend & Systems Engineer, Dhaka, Bangladesh.

- GitHub: https://github.com/alvi164
- ORCID: https://orcid.org/0009-0001-7332-6816

Copyright (c) 2026 Syad Mehedi Hasan Alvi. All rights reserved. The effective proprietary license date is 17 July 2026. See `LICENSE.txt`, `THIRD-PARTY-NOTICES.md`, and the lawyer-reviewable `COMMERCIAL-EULA-TEMPLATE.md` before commercial distribution.
