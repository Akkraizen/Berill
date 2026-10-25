#ifndef AppVersion
  #error AppVersion must be read from the release MSI
#endif
#ifndef MsiPath
  #error MsiPath must point to the release MSI
#endif
#ifndef ProductCode
  #error ProductCode must be read from the release MSI
#endif
#ifndef UpgradeCode
  #error UpgradeCode must be read from the release MSI
#endif
#ifndef OutputPath
  #define OutputPath "output"
#endif

[Setup]
AppId=Berill.Setup
AppName=Berill
AppVersion={#AppVersion}
AppPublisher=Akkraizen
DefaultDirName={autopf}\Berill
DefaultGroupName=Berill
OutputDir={#OutputPath}
OutputBaseFilename=Berill-{#AppVersion}-Setup
SetupIconFile=assets\Berill.ico
WizardStyle=modern dark includetitlebar
WizardSizePercent=120,120
WizardImageFile=
WizardSmallImageFile=assets\mark.png
ShowLanguageDialog=yes
LanguageDetectionMethod=uilanguage
UsePreviousLanguage=no
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=yes
DisableReadyPage=no
Uninstallable=no
CreateAppDir=no
UsePreviousAppDir=no
UsePreviousTasks=no
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
Compression=lzma2/fast
SolidCompression=yes
DiskSpanning=no
CloseApplications=no
RestartApplications=no
SetupLogging=yes
ExtraDiskSpaceRequired=300000000
#ifdef Preview
PrivilegesRequiredOverridesAllowed=commandline
#endif

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Messages]
english.WelcomeLabel1=Your space.%nEverything at hand.
english.WelcomeLabel2=Berill brings together notes, plans, habits and personal collections.%n%nThis wizard will install the app and help you create a shortcut.%n%nBefore updating, close Berill using Exit in its tray menu.
english.SelectDirLabel3=Choose where Berill will be installed.
english.ReadyLabel1=Everything is ready to install Berill.
english.FinishedHeadingLabel=Berill is ready
english.FinishedLabelNoIcons=The app has been installed.%n%nClosing the window keeps Berill in the system tray for quick access. To close the app completely, choose Exit in the tray menu.
english.BeveledLabel=BERILL  /  WINDOWS
russian.WelcomeLabel1=Ваше пространство.%nВсё под рукой.
russian.WelcomeLabel2=Berill объединяет заметки, планы, привычки и личные коллекции.%n%nМастер поможет установить приложение и создать ярлык.%n%nПеред обновлением завершите Berill через пункт «Выход» в трее.
russian.SelectDirLabel3=Выберите, где будет установлен Berill.
russian.ReadyLabel1=Всё готово к установке Berill.
russian.FinishedHeadingLabel=Berill готов к работе
russian.FinishedLabelNoIcons=Приложение установлено.%n%nКрестик окна скрывает Berill в трей для быстрого возвращения. Для полного завершения выберите «Выход» в меню значка.
russian.BeveledLabel=BERILL  /  WINDOWS

[CustomMessages]
english.DesktopShortcut=Create a desktop shortcut
english.QuickAccess=Quick access:
english.LaunchApp=Launch Berill
english.InstallTitle=Installing Berill
english.InstallDescription=Setting up the app on your computer.
english.InstallFiles=Installing application files and bundled Java
english.InstallWait=This may take a few minutes. Please wait for installation to finish.
english.ExistingInstallation=An existing Berill installation was found. Files will be updated in the current folder.
english.StartFailed=Could not start installation: %1
english.InstallBusy=Windows is already installing another application. Wait for it to finish and try again.
english.InstallCancelled=Installation was cancelled. Berill was not installed.
english.InstallFailed=Could not install Berill. Windows Installer code: %1. Log: %2
english.PreviewOnly=This is a visual preview. Installation is disabled in this build.
english.UpdateButton=Update
english.RepairButton=Reinstall
english.NewerInstalled=A newer version of Berill (%1) is already installed. Use the same or a newer installer.
english.DetectionFailed=Could not check the existing Berill installation: %1
english.VersionConflict=Windows found a conflicting Berill package. Use the latest installer. Log: %1
russian.DesktopShortcut=Создать ярлык на рабочем столе
russian.QuickAccess=Быстрый доступ:
russian.LaunchApp=Запустить Berill
russian.InstallTitle=Устанавливаем Berill
russian.InstallDescription=Настраиваем приложение на вашем компьютере.
russian.InstallFiles=Устанавливаем файлы и встроенную Java
russian.InstallWait=Это может занять несколько минут. Дождитесь завершения установки.
russian.ExistingInstallation=Обнаружена установленная версия Berill. Файлы будут обновлены в существующей папке.
russian.StartFailed=Не удалось запустить установку: %1
russian.InstallBusy=Windows уже выполняет другую установку. Дождитесь её завершения и повторите попытку.
russian.InstallCancelled=Установка отменена. Berill не был установлен.
russian.InstallFailed=Не удалось установить Berill. Код Windows Installer: %1. Журнал: %2
russian.PreviewOnly=Предпросмотр оформления. Установка в этой сборке отключена.
russian.UpdateButton=Обновить
russian.RepairButton=Переустановить
russian.NewerInstalled=Уже установлена более новая версия Berill (%1). Используйте установщик той же или более новой версии.
russian.DetectionFailed=Не удалось проверить существующую установку Berill: %1
russian.VersionConflict=Windows обнаружила конфликт пакетов Berill. Используйте актуальный установщик. Журнал: %1

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopShortcut}"; GroupDescription: "{cm:QuickAccess}";

[Files]
Source: "{#MsiPath}"; DestName: "Berill.msi"; Flags: dontcopy

[Run]
Filename: "{app}\Berill.exe"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent runasoriginaluser; Check: CanLaunch

[Code]
var
  InstallPage: TOutputMarqueeProgressWizardPage;
  Installed: Boolean;
  RestartRequired: Boolean;

#include "upgrade.iss"

function CanLaunch: Boolean;
begin
  Result := Installed and not RestartRequired and FileExists(ExpandConstant('{app}\Berill.exe'));
end;

procedure InitializeWizard;
begin
  InstallPage := CreateOutputMarqueeProgressPage(CustomMessage('InstallTitle'),
    CustomMessage('InstallDescription'));
  WizardForm.WizardBitmapImage.Visible := False;
  WizardForm.WizardBitmapImage2.Visible := False;
  WizardForm.WelcomeLabel1.Font.Size := 23;
  WizardForm.WelcomeLabel1.SetBounds(ScaleX(24), ScaleY(24),
    WizardForm.WelcomePage.ClientWidth - ScaleX(48), ScaleY(100));
  WizardForm.WelcomeLabel2.Left := WizardForm.WelcomeLabel1.Left;
  WizardForm.WelcomeLabel2.Width := WizardForm.WelcomeLabel1.Width;
  WizardForm.WelcomeLabel2.Top := WizardForm.WelcomeLabel1.Top + WizardForm.WelcomeLabel1.Height + ScaleY(12);
  WizardForm.WelcomeLabel2.Height := WizardForm.WelcomePage.Height - WizardForm.WelcomeLabel2.Top - ScaleY(20);
  WizardForm.FinishedHeadingLabel.Left := ScaleX(24);
  WizardForm.FinishedHeadingLabel.Width := WizardForm.FinishedPage.ClientWidth - ScaleX(48);
  WizardForm.FinishedLabel.Left := ScaleX(24);
  WizardForm.FinishedLabel.Width := WizardForm.FinishedHeadingLabel.Width;
  WizardForm.RunList.Left := ScaleX(24);
  WizardForm.RunList.Width := WizardForm.FinishedHeadingLabel.Width;
  WizardForm.YesRadio.Left := ScaleX(24);
  WizardForm.YesRadio.Width := WizardForm.FinishedHeadingLabel.Width;
  WizardForm.NoRadio.Left := ScaleX(24);
  WizardForm.NoRadio.Width := WizardForm.FinishedHeadingLabel.Width;
  FindInstalledBerill;
  if ExistingDirectory <> '' then begin
    WizardForm.DirEdit.Text := ExistingDirectory;
    WizardForm.DirEdit.Enabled := False;
    WizardForm.DirBrowseButton.Enabled := False;
    WizardForm.SelectDirLabel.Caption := CustomMessage('ExistingInstallation');
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
var
  Mode: Integer;
begin
  if CurPageID = wpReady then begin
    Mode := UpdateMode(ExistingProduct, ExistingVersion, '{#ProductCode}', '{#AppVersion}');
    if Mode = 1 then WizardForm.NextButton.Caption := CustomMessage('RepairButton')
    else if Mode = 2 then WizardForm.NextButton.Caption := CustomMessage('UpdateButton');
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ExitCode, Mode: Integer;
  Params, Shortcut, LogFile: String;
  Started: Boolean;
begin
  Result := '';
  if Installed then Exit;
  FindInstalledBerill;
  if DetectionError <> '' then begin
    Result := FmtMessage(CustomMessage('DetectionFailed'), [DetectionError]);
    Exit;
  end;
  Mode := UpdateMode(ExistingProduct, ExistingVersion, '{#ProductCode}', '{#AppVersion}');
  if Mode = -1 then begin
    Result := FmtMessage(CustomMessage('NewerInstalled'), [ExistingVersion]);
    Exit;
  end;
  if ExistingDirectory <> '' then WizardForm.DirEdit.Text := ExistingDirectory;
  if WizardIsTaskSelected('desktopicon') then Shortcut := '1' else Shortcut := '0';
  ForceDirectories(ExpandConstant('{localappdata}\Berill\Installer Logs'));
  LogFile := ExpandConstant('{localappdata}\Berill\Installer Logs\install-') +
    GetDateTimeString('yyyymmdd-hhnnss', '-', ':') + '.log';
  InstallPage.SetText(CustomMessage('InstallFiles'), CustomMessage('InstallWait'));
  InstallPage.Show;
  InstallPage.Animate;
  try
    ExtractTemporaryFile('Berill.msi');
    Params := '/i "' + ExpandConstant('{tmp}\Berill.msi') + '" /qn /norestart' +
      ' /L*v "' + LogFile + '" INSTALLDIR="' + RemoveBackslashUnlessRoot(WizardDirValue) + '"' +
      ' JP_INSTALL_DESKTOP_SHORTCUT=' + Shortcut + ' JP_INSTALL_STARTMENU_SHORTCUT=1';
    if Mode = 1 then
      Params := Params + ' REINSTALL=ALL REINSTALLMODE=vomus';
    Started := Exec(ExpandConstant('{sys}\msiexec.exe'), Params, '', SW_HIDE,
      ewWaitUntilTerminated, ExitCode);
    if not Started then
      Result := FmtMessage(CustomMessage('StartFailed'), [SysErrorMessage(ExitCode)])
    else if (ExitCode = 0) or (ExitCode = 3010) then begin
      Installed := True;
      RestartRequired := ExitCode = 3010;
    end else if ExitCode = 1618 then
      Result := CustomMessage('InstallBusy')
    else if ExitCode = 1602 then
      Result := CustomMessage('InstallCancelled')
    else if ExitCode = 1638 then
      Result := FmtMessage(CustomMessage('VersionConflict'), [LogFile])
    else
      Result := FmtMessage(CustomMessage('InstallFailed'), [IntToStr(ExitCode), LogFile]);
    Log('Windows Installer exit code: ' + IntToStr(ExitCode));
  finally
    InstallPage.Hide;
  end;
end;

function NeedRestart: Boolean;
begin
  Result := RestartRequired;
end;

#ifdef Preview
function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := CurPageID <> wpReady;
  if not Result then
    MsgBox(CustomMessage('PreviewOnly'), mbInformation, MB_OK);
end;
#endif
