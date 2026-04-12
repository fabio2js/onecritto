===============================================================
                  OneCritto v2.9.2 - README
              Linux / Portable Distribution
===============================================================

  Secure, offline, local password manager.

===============================================================
  PACKAGE CONTENTS
===============================================================

  OneCritto/
  ├── bin/                      Bundled JRE binaries (Java 21)
  ├── lib/                      Bundled JRE libraries
  └── OneCritto.sh              Launch script (Linux )

===============================================================
  QUICK START
===============================================================

  1. Extract the entire archive to a directory of your choice,
     for example:

        unzip OneCritto-2.9.2-linux.zip -d ~/

  2. Make the launch script executable (first time only):

        chmod +x ~/OneCritto/OneCritto.sh

  3. Run OneCritto:

        ~/OneCritto/OneCritto.sh

  NOTE: You do NOT need Java installed on your system.
        The "bin" and "lib" folders already include everything
        required to run the application.

===============================================================
  SYSTEM REQUIREMENTS
===============================================================

  - Linux (x86_64) with glibc 2.17+
  - At least 200 MB of free disk space
  - At least 2 GB of available RAM
  - A desktop environment with X11 or Wayland support

===============================================================
  IMPORTANT NOTES
===============================================================

  * Do NOT delete or rename the "bin" or "lib" folders.
    They contain the Java Runtime required to run the app.

  * Vault files (.critto) are saved in the location chosen
    by the user. Always keep a secure backup.

  * Your master password is NOT recoverable in any way.
    If you forget it, your vault data will be permanently
    inaccessible.

  * OneCritto works entirely offline.
    No data is ever sent to external servers.

===============================================================
  TROUBLESHOOTING
===============================================================

  Error: "Permission denied"
  -> Make the script executable:
        chmod +x OneCritto.sh

  Error: JRE not found
  -> Verify that the "bin/" directory exists and contains
     the "java" executable.

  Application does not start / no window appears:
  -> Run from a terminal to see error output:
        cd ~/OneCritto
        ./OneCritto.sh

  -> On Wayland, if you experience display issues try:
        GDK_BACKEND=x11 ./OneCritto.sh

  -> Ensure your system has a supported desktop environment
     (GNOME, KDE, XFCE, etc.).

===============================================================
  LICENSE
===============================================================

  Please review the End User License Agreement at:
  https://onecritto.com/eula.html

===============================================================
  (c) 2025-2026 OneCritto. All rights reserved.
===============================================================
