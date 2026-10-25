$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$source = Get-Content -LiteralPath (Join-Path $projectRoot 'app\desktopApp\src\main\kotlin\dev\akkraizen\berill\desktop\ui\BerillIcon.kt') -Raw
$assets = Join-Path $PSScriptRoot 'assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$paths = @{}
foreach ($match in [regex]::Matches($source, 'private val (\w+) = Path\(\)\.apply \{([\s\S]*?)\r?\n  \}')) {
  $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
  $point = [System.Drawing.PointF]::Empty
  foreach ($command in [regex]::Matches($match.Groups[2].Value, '(moveTo|lineTo|cubicTo|close)\(([^)]*)\)')) {
    $values = @($command.Groups[2].Value -split ',' | Where-Object { $_.Trim() } | ForEach-Object {
      [float]::Parse($_.Trim().TrimEnd('f'), [Globalization.CultureInfo]::InvariantCulture)
    })
    switch ($command.Groups[1].Value) {
      'moveTo' { $path.StartFigure(); $point = [System.Drawing.PointF]::new($values[0], $values[1]) }
      'lineTo' {
        $next = [System.Drawing.PointF]::new($values[0], $values[1])
        $path.AddLine($point, $next)
        $point = $next
      }
      'cubicTo' {
        $next = [System.Drawing.PointF]::new($values[4], $values[5])
        $path.AddBezier($point, [System.Drawing.PointF]::new($values[0], $values[1]),
          [System.Drawing.PointF]::new($values[2], $values[3]), $next)
        $point = $next
      }
      'close' { $path.CloseFigure() }
    }
  }
  $paths[$match.Groups[1].Value] = $path
}
$layers = @([regex]::Matches($source, 'drawPath\((\w+), Color\(0xFF([0-9A-Fa-f]{6})\)\)'))
if ($paths.Count -ne 4 -or $layers.Count -ne 4 -or -not $paths.ContainsKey('body')) {
  throw 'The Compose icon format changed. Update the asset generator before building the installer.'
}
function New-BerillBitmap([int]$Size) {
  $bitmap = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $graphics.SmoothingMode = 'AntiAlias'
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $scale = $Size / 384.0
    $graphics.TranslateTransform([float](($Size - 256 * $scale) / 2), 0)
    $graphics.ScaleTransform([float]$scale, [float]$scale)
    $graphics.SetClip($paths['body'])
    foreach ($layer in $layers) {
      $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#' + $layer.Groups[2].Value))
      try { $graphics.FillPath($brush, $paths[$layer.Groups[1].Value]) } finally { $brush.Dispose() }
    }
  } finally { $graphics.Dispose() }
  return $bitmap
}
try {
  $frames = @()
  foreach ($size in @(16, 20, 24, 32, 40, 48, 64, 128, 256)) {
    $bitmap = New-BerillBitmap $size
    $stream = [System.IO.MemoryStream]::new()
    try {
      $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
      $frames += @{ Size = $size; Bytes = $stream.ToArray() }
    } finally { $stream.Dispose(); $bitmap.Dispose() }
  }
  $file = [System.IO.File]::Create((Join-Path $assets 'Berill.ico'))
  $writer = [System.IO.BinaryWriter]::new($file)
  try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$frames.Count)
    $offset = 6 + 16 * $frames.Count
    foreach ($frame in $frames) {
      $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
      $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
      $writer.Write([byte]0); $writer.Write([byte]0)
      $writer.Write([uint16]1); $writer.Write([uint16]32)
      $writer.Write([uint32]$frame.Bytes.Length); $writer.Write([uint32]$offset)
      $offset += $frame.Bytes.Length
    }
    foreach ($frame in $frames) { $writer.Write([byte[]]$frame.Bytes) }
  } finally { $writer.Dispose() }
  $bitmap = New-BerillBitmap 128
  try { $bitmap.Save((Join-Path $assets 'mark.png'), [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $bitmap.Dispose() }
} finally {
  foreach ($path in $paths.Values) { $path.Dispose() }
}
