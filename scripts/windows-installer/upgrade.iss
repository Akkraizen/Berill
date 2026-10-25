var
  ExistingProduct: String;
  ExistingVersion: String;
  ExistingDirectory: String;
  DetectionError: String;

function UpdateMode(Product, Version, TargetProduct, TargetVersion: String): Integer;
var
  Current, Target: Int64;
begin
  Result := 0;
  if Product = '' then Exit;
  if not StrToVersion(Version, Current) or not StrToVersion(TargetVersion, Target) then
    RaiseException('Invalid MSI version');
  if ComparePackedVersion(Current, Target) > 0 then
    Result := -1
  else if CompareText(Product, TargetProduct) = 0 then
    Result := 1
  else
    Result := 2;
end;

procedure FindInstalledBerill;
var
  Installer, Products: Variant;
  Index: Integer;
  Product, Version: String;
  Candidate, Selected: Int64;
begin
  ExistingProduct := '';
  ExistingVersion := '';
  ExistingDirectory := '';
  DetectionError := '';
  Selected := 0;
  try
    Installer := CreateOleObject('WindowsInstaller.Installer');
    Products := Installer.RelatedProducts('{#UpgradeCode}');
    for Index := 0 to Products.Count - 1 do begin
      Product := Products.Item(Index);
      if Installer.ProductState(Product) = 5 then begin
        Version := Installer.ProductInfo(Product, 'VersionString');
        if not StrToVersion(Version, Candidate) then RaiseException('Invalid installed MSI version');
        if (ExistingProduct = '') or (ComparePackedVersion(Candidate, Selected) > 0) or
          ((Candidate = Selected) and (CompareText(Product, '{#ProductCode}') = 0)) then begin
          ExistingProduct := Product;
          ExistingVersion := Version;
          ExistingDirectory := Installer.ProductInfo(Product, 'InstallLocation');
          Selected := Candidate;
        end;
      end;
    end;
    Log('Related Berill product: ' + ExistingProduct + '; version: ' + ExistingVersion);
  except
    DetectionError := GetExceptionMessage;
    Log('Installed product detection failed: ' + DetectionError);
  end;
end;

#ifdef UpgradeCheck
function InitializeSetup: Boolean;
begin
  if UpdateMode('', '', '{#ProductCode}', '{#AppVersion}') <> 0 then RaiseException('Fresh install check failed');
  if UpdateMode('{#ProductCode}', '{#AppVersion}', '{#ProductCode}', '{#AppVersion}') <> 1 then RaiseException('Repair check failed');
  if UpdateMode('{00000000-0000-0000-0000-000000000001}', '1.0.0', '{#ProductCode}', '1.0.1') <> 2 then RaiseException('Upgrade check failed');
  if UpdateMode('{00000000-0000-0000-0000-000000000001}', '1.0.1', '{#ProductCode}', '1.0.1') <> 2 then RaiseException('Same-version upgrade check failed');
  if UpdateMode('{00000000-0000-0000-0000-000000000001}', '1.0.2', '{#ProductCode}', '1.0.1') <> -1 then RaiseException('Downgrade check failed');
  FindInstalledBerill;
  if DetectionError <> '' then RaiseException(DetectionError);
  if ExistingProduct = '' then RaiseException('Expected existing Berill installation was not detected');
  if ExistingDirectory = '' then RaiseException('Existing installation directory is missing');
  Log('UPGRADE_CHECK_PASS mode=' + IntToStr(UpdateMode(ExistingProduct, ExistingVersion, '{#ProductCode}', '{#AppVersion}')) +
    '; directory=' + ExistingDirectory);
  Result := False;
end;
#endif
