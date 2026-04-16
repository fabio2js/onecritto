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


### CSV Password Import
- Import passwords from **Chrome**, **Bitwarden**, **KeePass**, **LastPass**, and generic CSV files
- Auto-detection of the source format — no manual configuration needed
- Supports comma and semicolon delimiters, UTF-8 BOM handling
- Field mapping engine automatically maps columns to OneCritto entry fields
- Preview imported entries before committing to the vault
- Duplicates and conflicts highlighted during import

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

## Build from Source

If you prefer to build and run OneCritto from source, follow the steps below. The project uses **Java 21** and **Maven**, with JavaFX 21 pulled in as a Maven dependency — no separate JavaFX SDK required.

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 21 (LTS) | [Temurin](https://adoptium.net/temurin/releases/?version=21), [Liberica](https://bell-sw.com/pages/downloads/), or any OpenJDK 21 build |
| **Maven** | 3.9+ | Or use the bundled `mvnw` wrapper |
| **Git** | any recent | To clone the repository |

Verify your environment:

```bash
java -version     # should report 21.x
mvn -version      # should report 3.9+ and JDK 21
```

If `java -version` does not report 21, set `JAVA_HOME` to your JDK 21 installation and add `$JAVA_HOME/bin` to the `PATH`.

### Step-by-step

1. **Clone the repository**

   ```bash
   git clone https://github.com/<your-org>/onecrittoV4.git
   cd onecrittoV4
   ```

2. **Run the application**

   ```bash
   mvn javafx:run
   ```

   This launches OneCritto via the `javafx-maven-plugin`, which resolves the JavaFX modules and starts the main class `com.onecritto.ui.MainApp`.


### Windows (PowerShell) equivalent

```powershell
git clone https://github.com/<your-org>/onecrittoV4.git
cd onecrittoV4
.\mvnw.cmd javafx:run
```

---

## License

OneCritto is released under the **GNU General Public License v3.0 (GPLv3)**.
See [EULA.md](EULA.md) for the full license summary and disclaimers, or the online version at [onecritto.com/eula.html](https://onecritto.com/eula.html).

© 2025–2026 OneCritto.
