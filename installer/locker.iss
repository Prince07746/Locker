; Inno Setup script for the Locker Content Guardian agent (Windows).
; Build with Inno Setup 6+ (iscc locker.iss) AFTER you have compiled
; locker-agent.exe and signed it. See agent/README.md.
;
; This installer:
;   * collects the server URL, device ID, and device token,
;   * installs the agent + blocklist,
;   * disables browser DNS-over-HTTPS (so the DNS filter cannot be tunnelled),
;   * registers the auto-restarting Windows service,
;   * and routes Add/Remove Programs uninstall through the guardian-authorized
;     flow, so protection cannot be removed without the emailed code.

#define AppName "Locker Content Protection"
#define AppVersion "1.0.0"
#define ExeName "locker-agent.exe"

[Setup]
AppId={{B4E2B6A1-9C3D-4E77-9E2A-LOCKER0001}}
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={autopf}\Locker
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
Uninstallable=yes
OutputBaseFilename=LockerSetup
Compression=lzma2
SolidCompression=yes
; SignTool=... ; configure your EV code-signing tool here for production

[Files]
Source: "..\agent\locker-agent.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "blocklist.txt"; DestDir: "{commonappdata}\Locker"; Flags: onlyifdoesntexist

[Registry]
; Disable DNS-over-HTTPS so browsers resolve through the system (our) resolver.
Root: HKLM; Subkey: "SOFTWARE\Policies\Google\Chrome"; ValueType: string; ValueName: "DnsOverHttpsMode"; ValueData: "off"; Flags: uninsdeletevalue
Root: HKLM; Subkey: "SOFTWARE\Policies\Microsoft\Edge"; ValueType: string; ValueName: "DnsOverHttpsMode"; ValueData: "off"; Flags: uninsdeletevalue
Root: HKLM; Subkey: "SOFTWARE\Policies\Mozilla\Firefox\DNSOverHTTPS"; ValueType: dword; ValueName: "Enabled"; ValueData: 0; Flags: uninsdeletevalue
Root: HKLM; Subkey: "SOFTWARE\Policies\Mozilla\Firefox\DNSOverHTTPS"; ValueType: dword; ValueName: "Locked"; ValueData: 1; Flags: uninsdeletevalue

[Code]
var
  ConfigPage: TInputQueryWizardPage;

procedure InitializeWizard();
begin
  ConfigPage := CreateInputQueryPage(wpSelectDir,
    'Device enrollment',
    'Connect this device to your Content Guardian server',
    'Enter the values from your guardian dashboard. The device token is shown only once when you add a device.');
  ConfigPage.Add('Server URL (e.g. https://myguardian.duckdns.org):', False);
  ConfigPage.Add('Device ID:', False);
  ConfigPage.Add('Device token:', True);  { masked }
end;

function JsonEscape(const S: string): string;
begin
  Result := S;
  StringChangeEx(Result, '\', '\\', True);
  StringChangeEx(Result, '"', '\"', True);
end;

procedure WriteConfig();
var
  Dir, Path, Json: string;
begin
  Dir := ExpandConstant('{commonappdata}\Locker');
  ForceDirectories(Dir);
  Path := Dir + '\config.json';
  Json :=
    '{' + #13#10 +
    '  "serverBaseUrl": "' + JsonEscape(ConfigPage.Values[0]) + '",' + #13#10 +
    '  "deviceId": "'      + JsonEscape(ConfigPage.Values[1]) + '",' + #13#10 +
    '  "deviceToken": "'   + JsonEscape(ConfigPage.Values[2]) + '",' + #13#10 +
    '  "upstreamDns": "9.9.9.9:53",' + #13#10 +
    '  "listenAddr": "127.0.0.1:53",' + #13#10 +
    '  "heartbeatSeconds": 60' + #13#10 +
    '}';
  SaveStringToFile(Path, Json, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    WriteConfig();
    { Register and start the auto-restarting service. }
    Exec(ExpandConstant('{app}\{#ExeName}'), 'install-service', '',
         SW_HIDE, ewWaitUntilTerminated, ResultCode);
  end;
end;

{ Route uninstall through the guardian-authorized flow. If the server denies
  removal (wrong/absent code), abort the uninstall entirely. }
function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
begin
  if Exec(ExpandConstant('{app}\{#ExeName}'), 'uninstall', '',
          SW_SHOW, ewWaitUntilTerminated, ResultCode) then
  begin
    if ResultCode = 0 then
      Result := True
    else
    begin
      MsgBox('Removal was not authorized by the guardian. '
           + 'Content protection remains installed.', mbError, MB_OK);
      Result := False;
    end;
  end
  else
  begin
    MsgBox('Could not run the authorization step. Removal cancelled.', mbError, MB_OK);
    Result := False;
  end;
end;
