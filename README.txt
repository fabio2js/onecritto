===============================================================
                  OneCritto v2.9.3 - README
===============================================================

  Secure, offline, local password manager.

===============================================================
  ZIP CONTENTS
===============================================================

  OneCritto/
  ├── runtime/                  Bundled JRE (Java 21)
  ├── OneCritto.bat             Quick launch (Windows)
  ├── OneCritto.ico             Application icon
  └── onecritto-vault-all-2.9.3.jar   Application

===============================================================
  QUICK START
===============================================================

  1. Extract the entire zip contents to a folder of your
     choice (e.g. C:\OneCritto).

  2. Double-click  OneCritto.bat  to launch the application.

     Alternatively, from Command Prompt / PowerShell:

        cd C:\OneCritto
        OneCritto.bat

  NOTE: You do NOT need Java installed on your PC.
        The "runtime" folder already includes everything
        required to run the application.

===============================================================
  SYSTEM REQUIREMENTS
===============================================================

  - Windows 10 / 11 (64-bit)
  - At least 200 MB of free disk space
  - At least 2 GB of available RAM

===============================================================
  IMPORTANT NOTES
===============================================================

  • Do NOT delete or rename the "runtime" folder.
    It contains the Java Runtime required to run the app.

  • Do NOT delete the file  onecritto-vault-all-2.9.3.jar.
    It is the core of the application.

  • Vault files (.critto) are saved in the location chosen
    by the user. Always keep a secure backup.

  • Your master password is NOT recoverable in any way.
    If you forget it, your vault data will be permanently
    inaccessible.

  • OneCritto works entirely offline.
    No data is ever sent to external servers.

===============================================================
  TROUBLESHOOTING
===============================================================

  Error: "JRE non trovato" (JRE not found)
  → Make sure the "runtime\bin" folder exists and has not
    been moved or deleted.

  Error: "onecritto-vault-all-2.9.3.jar non trovato" (JAR not found)
  → Make sure the JAR file is in the same folder as
    OneCritto.bat.

  Application does not start / window does not appear:
  → Try launching from Command Prompt to see error messages:
        cd <installation_folder>
        OneCritto.bat

  → Verify that your OS is Windows 64-bit.

===============================================================
  LICENSE
===============================================================

  Please review the End User License Agreement at:
  https://onecritto.com/eula.html

===============================================================
  (c) 2025-2026 OneCritto. All rights reserved.
===============================================================
