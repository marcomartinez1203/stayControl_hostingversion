# Iconos derivados de la llave SVG del login. No requiere paquetes ni servicios externos.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$destino = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\frontend\assets\icons'))
foreach ($tamano in @(180, 192, 512)) {
    $bitmap = [Drawing.Bitmap]::new($tamano, $tamano)
    $grafico = [Drawing.Graphics]::FromImage($bitmap)
    $trazo = [Drawing.Pen]::new([Drawing.Color]::White, 1.8)
    try {
        $grafico.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $grafico.Clear([Drawing.ColorTranslator]::FromHtml('#0f172a'))
        # Margen de seguridad para recortes maskable: símbolo centrado en el 62.5%.
        $escala = $tamano / 51.2
        $grafico.TranslateTransform([single]($tamano * .1875), [single]($tamano * .1875))
        $grafico.ScaleTransform([single]$escala, [single]$escala)
        $trazo.StartCap = [Drawing.Drawing2D.LineCap]::Round
        $trazo.EndCap = [Drawing.Drawing2D.LineCap]::Round
        $grafico.DrawEllipse($trazo, [single]4.3, [single]13.3, [single]14.4, [single]14.4)
        $grafico.DrawLine($trazo, [single]16.5, [single]15.5, [single]25.5, [single]6.5)
        $grafico.DrawLine($trazo, [single]22, [single]10, [single]25, [single]13)
        $grafico.DrawLine($trazo, [single]24.5, [single]7.5, [single]27, [single]10)
        $bitmap.Save((Join-Path $destino "pwa-$tamano.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $trazo.Dispose(); $grafico.Dispose(); $bitmap.Dispose() }
}
