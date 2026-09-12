; InvoiceStudio - classic setup.exe wrapper around the jpackage app-image.
;
; Usage (on a Windows machine):
;   1) .\packaging\build-windows-installer.ps1 -AppImage     (builds app-image\InvoiceStudio\)
;   2) ISCC.exe packaging\InvoiceStudio.iss                  (Inno Setup 6 compiler)
;      -> packaging\dist\InvoiceStudio-3.0.0-setup.exe
;
; The produced setup.exe behaves like any mainstream installer: dir chooser,
; desktop-icon task, start-menu group, uninstaller, launch-after-install.

#define AppName "InvoiceStudio"
#ifndef AppVersion
  #define AppVersion "4.0.0"
#endif
#define AppExe "InvoiceStudio.exe"

[Setup]
AppId={{7A1F4C93-6D2E-4B58-9A0F-2C3D4E5F6A7B}}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppName}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=dist
OutputBaseFilename={#AppName}-{#AppVersion}-setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; \
    GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
Source: "app-image\{#AppName}\*"; DestDir: "{app}"; \
    Flags: recursesubdirs createallsubdirs replacesameversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; \
    Flags: nowait postinstall skipifsilent
