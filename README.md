<p align="center">
  <img src="src/main/resources/icons/onecritto_white_key_128x128.png" alt="OneCritto" width="128" />
</p>

# OneCritto

**Secure, offline, local password manager.**

[OneCritto](https://onecritto.com) is a desktop password manager built with JavaFX 21 that keeps your credentials and sensitive files encrypted on your device. No cloud, no accounts, no data leaves your machine.

[Download](https://onecritto.com/download.html) · [User Guide](https://onecritto.com/guide.html)

---

## Key Features

### Encrypted Vault
- All data stored in a single `.onecritto` file, encrypted with **AES-256-GCM**
- Key derivation via **Argon2id** (64 MB RAM, 3 iterations) — resistant to brute-force attacks
- **HMAC-SHA256** integrity check to detect tampering
- Auto-save on every change

### Password Management
- Store credentials with title, username, password, notes, and category
- Real-time password strength meter
- Instant search and filtering across all entries
- Masked fields by default — reveal on demand with auto-hide timer

### Encrypted File Storage
- Attach and encrypt any file directly inside the vault
- Streaming encryption via Bouncy Castle GCM — no full file loaded in RAM
- Export decrypted copies when needed
- Progress bar with speed and ETA

### Password Generator
- **Strong mode**: configurable length (8–40), upper/lower/digits/symbols, ambiguous character avoidance
- **Mnemonic mode**: pronounceable syllable-based passwords
- Real-time strength feedback

### Sentinel Security Engine
- **Password Analyzer**: entropy calculation, common password dictionary, leet-speak detection, keyboard pattern detection, duplicate detection, score 0–100
- **Breach Check**: Have I Been Pwned integration using k-anonymity — your password never leaves the device
- **Password Coach**: personalized tips per entry (critical / warning / info)
- **Dashboard**: vault health score, rotation plan for weak/old/duplicate passwords


### SSH Connection Manager
- Save SSH connection profiles (name, host, port, username, private key) inside the encrypted vault
- Private keys stored as encrypted files in the vault — decrypted on the fly when connecting
- One-click connect: launches a terminal session with the correct `ssh` command
- Copy SSH command to clipboard
- Key file permissions automatically set to `600` for SSH compatibility

### Security by Design
- **Auto-lock** after 3 minutes of inactivity — keys wiped from RAM
- **Manual lock** with `Ctrl+L`
- **Secure fields**: sensitive data held in `char[]`, wiped on dispose; clipboard auto-cleared after 20 seconds
- **Secure temp files**: overwritten with random data before deletion
- **File permissions**: `chmod 600` on Linux/macOS, owner-only ACL on Windows NTFS

### Cross-Platform
- Windows, Linux
- Bundled JRE — no Java installation required
- Dark theme UI

---

## System Requirements

| | Minimum |
|---|---|
| **OS** | Windows 10/11, Linux (64-bit) |
| **Disk** | 200 MB free |
| **RAM** | 2 GB available |

---

## Quick Start (Windows)

1. Download and extract the zip
2. Double-click `OneCritto.bat`
3. Create a new vault with a strong master password

> The bundled `runtime/` folder contains everything needed — no Java installation required.

---

## License

See the [End User License Agreement](https://onecritto.com/eula.html).

© 2025–2026 OneCritto. All rights reserved.
