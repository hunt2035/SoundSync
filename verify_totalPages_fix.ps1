# totalPages字段修复验证脚本
# 用于验证修复是否成功

Write-Host "=== totalPages字段修复验证 ===" -ForegroundColor Green

# 1. 检查构建是否成功
Write-Host "`n1. 检查构建状态..." -ForegroundColor Yellow
if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    $apkSize = (Get-Item "app\build\outputs\apk\debug\app-debug.apk").Length
    $apkSizeMB = [math]::Round($apkSize / 1MB, 2)
    Write-Host "✓ APK构建成功，大小: ${apkSizeMB}MB" -ForegroundColor Green
} else {
    Write-Host "✗ APK构建失败" -ForegroundColor Red
    exit 1
}

# 2. 检查修复的文件是否存在
Write-Host "`n2. 检查修复的文件..." -ForegroundColor Yellow

$fixedFiles = @(
    "app\src\main\java\org\soundsync\ebook\ui\bookshelf\BookshelfViewModel.kt",
    "app\src\main\java\org\soundsync\ebook\ui\ocrimport\OcrImportViewModel.kt",
    "app\src\main\java\org\soundsync\ebook\ui\components\NewTextDialog.kt"
)

foreach ($file in $fixedFiles) {
    if (Test-Path $file) {
        Write-Host "✓ $file 存在" -ForegroundColor Green
    } else {
        Write-Host "✗ $file 不存在" -ForegroundColor Red
    }
}

# 3. 检查关键修复内容
Write-Host "`n3. 检查关键修复内容..." -ForegroundColor Yellow

# 检查BookshelfViewModel中的totalPages设置
$bookshelfContent = Get-Content "app\src\main\java\org\soundsync\ebook\ui\bookshelf\BookshelfViewModel.kt" -Raw
if ($bookshelfContent -match "totalPages\s*=\s*totalPages") {
    Write-Host "✓ BookshelfViewModel: totalPages字段设置已修复" -ForegroundColor Green
} else {
    Write-Host "✗ BookshelfViewModel: totalPages字段设置未找到" -ForegroundColor Red
}

# 检查estimateBookPages方法
if ($bookshelfContent -match "private fun estimateBookPages") {
    Write-Host "✓ BookshelfViewModel: estimateBookPages方法已添加" -ForegroundColor Green
} else {
    Write-Host "✗ BookshelfViewModel: estimateBookPages方法未找到" -ForegroundColor Red
}

# 检查OCR导入修复
$ocrContent = Get-Content "app\src\main\java\org\soundsync\ebook\ui\ocrimport\OcrImportViewModel.kt" -Raw
if ($ocrContent -match "totalPages\s*=\s*totalPages") {
    Write-Host "✓ OcrImportViewModel: totalPages字段设置已修复" -ForegroundColor Green
} else {
    Write-Host "✗ OcrImportViewModel: totalPages字段设置未找到" -ForegroundColor Red
}

# 检查新建文本修复
$newTextContent = Get-Content "app\src\main\java\org\soundsync\ebook\ui\components\NewTextDialog.kt" -Raw
if ($newTextContent -match "totalPages\s*=\s*totalPages") {
    Write-Host "✓ NewTextDialog: totalPages字段设置已修复" -ForegroundColor Green
} else {
    Write-Host "✗ NewTextDialog: totalPages字段设置未找到" -ForegroundColor Red
}

# 4. 检查页数计算逻辑
Write-Host "`n4. 检查页数计算逻辑..." -ForegroundColor Yellow

if ($bookshelfContent -match "file\.length\(\)\s*/\s*2000") {
    Write-Host "✓ TXT文件页数计算逻辑正确 (每2000字符一页)" -ForegroundColor Green
} else {
    Write-Host "✗ TXT文件页数计算逻辑未找到" -ForegroundColor Red
}

# 5. 检查changelog更新
Write-Host "`n5. 检查文档更新..." -ForegroundColor Yellow

if (Test-Path "changelog.md") {
    $changelogContent = Get-Content "changelog.md" -Raw
    if ($changelogContent -match "totalPages字段为0的问题") {
        Write-Host "✓ changelog.md已更新修复记录" -ForegroundColor Green
    } else {
        Write-Host "✗ changelog.md未找到修复记录" -ForegroundColor Red
    }
} else {
    Write-Host "✗ changelog.md文件不存在" -ForegroundColor Red
}

if (Test-Path "test_totalPages_fix.md") {
    Write-Host "✓ 测试验证文档已创建" -ForegroundColor Green
} else {
    Write-Host "✗ 测试验证文档未找到" -ForegroundColor Red
}

# 6. 总结
Write-Host "`n=== 修复验证总结 ===" -ForegroundColor Green
Write-Host "修复内容:" -ForegroundColor Yellow
Write-Host "- 网页导入功能中添加totalPages字段计算"
Write-Host "- OCR导入功能中添加totalPages字段计算"
Write-Host "- 新建文本功能中添加totalPages字段计算"
Write-Host "- BookshelfViewModel.addBook方法中添加totalPages字段计算"
Write-Host "- 添加estimateBookPages方法用于统一页数估算"
Write-Host "- 统一使用每2000字符算一页的标准"

Write-Host "`n预期效果:" -ForegroundColor Yellow
Write-Host "- 导入文本文件后totalPages字段不再为0"
Write-Host "- 书架界面正确显示阅读进度百分比"
Write-Host "- 阅读进度计算公式: progress = (lastReadPage / totalPages) * 100%"

Write-Host "`n下一步测试建议:" -ForegroundColor Yellow
Write-Host "1. 安装APK到测试设备"
Write-Host "2. 测试网页导入功能"
Write-Host "3. 测试OCR导入功能"
Write-Host "4. 测试新建文本功能"
Write-Host "5. 检查书架界面阅读进度显示"
Write-Host "6. 验证数据库中totalPages字段值"

Write-Host "`n修复验证完成!" -ForegroundColor Green
