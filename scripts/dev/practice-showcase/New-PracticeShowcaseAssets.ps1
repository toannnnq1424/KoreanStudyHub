[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$showcaseRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$assetRoot = Join-Path $showcaseRoot 'assets'
$imageRoot = Join-Path $assetRoot 'images'
$audioRoot = Join-Path $assetRoot 'audio'

$null = New-Item -ItemType Directory -Force -Path $imageRoot
$null = New-Item -ItemType Directory -Force -Path $audioRoot

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Speech

function New-ShowcaseBitmap {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Eyebrow,
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string]$Subtitle,
        [Parameter(Mandatory = $true)][string]$AccentHex,
        [string[]]$Facts = @()
    )

    $width = 1280
    $height = 720
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $background = [System.Drawing.ColorTranslator]::FromHtml('#F7F9FC')
    $ink = [System.Drawing.ColorTranslator]::FromHtml('#172033')
    $muted = [System.Drawing.ColorTranslator]::FromHtml('#667085')
    $line = [System.Drawing.ColorTranslator]::FromHtml('#DDE4F0')
    $accent = [System.Drawing.ColorTranslator]::FromHtml($AccentHex)
    $accentSoft = [System.Drawing.Color]::FromArgb(28, $accent)

    $graphics.Clear($background)

    $accentBrush = New-Object System.Drawing.SolidBrush($accent)
    $accentSoftBrush = New-Object System.Drawing.SolidBrush($accentSoft)
    $inkBrush = New-Object System.Drawing.SolidBrush($ink)
    $mutedBrush = New-Object System.Drawing.SolidBrush($muted)
    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $linePen = New-Object System.Drawing.Pen($line, 2)
    $accentPen = New-Object System.Drawing.Pen($accent, 5)

    $fontFamily = 'Malgun Gothic'
    $eyebrowFont = New-Object System.Drawing.Font($fontFamily, 19, [System.Drawing.FontStyle]::Bold)
    $titleFont = New-Object System.Drawing.Font($fontFamily, 42, [System.Drawing.FontStyle]::Bold)
    $subtitleFont = New-Object System.Drawing.Font($fontFamily, 22, [System.Drawing.FontStyle]::Regular)
    $factFont = New-Object System.Drawing.Font($fontFamily, 19, [System.Drawing.FontStyle]::Regular)
    $brandFont = New-Object System.Drawing.Font($fontFamily, 22, [System.Drawing.FontStyle]::Bold)

    try {
        $graphics.FillRectangle($accentSoftBrush, 0, 0, 1280, 720)
        $graphics.FillRectangle($whiteBrush, 56, 54, 1168, 612)
        $graphics.DrawRectangle($linePen, 56, 54, 1168, 612)
        $graphics.FillRectangle($accentBrush, 56, 54, 18, 612)

        $graphics.DrawString($Eyebrow, $eyebrowFont, $accentBrush, 108, 100)
        $graphics.DrawString($Title, $titleFont, $inkBrush, 108, 152)
        $graphics.DrawString($Subtitle, $subtitleFont, $mutedBrush, 110, 226)
        $graphics.DrawLine($accentPen, 110, 286, 452, 286)

        $factY = 332
        foreach ($fact in $Facts) {
            $graphics.FillEllipse($accentBrush, 112, $factY + 8, 12, 12)
            $graphics.DrawString($fact, $factFont, $inkBrush, 146, $factY)
            $factY += 58
        }

        $graphics.FillRectangle($accentBrush, 930, 490, 208, 92)
        $graphics.DrawString('KSH', $brandFont, $whiteBrush, 995, 516)
        $graphics.DrawString('Practice Showcase · Original demo material', $factFont, $mutedBrush, 108, 603)

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $brandFont.Dispose()
        $factFont.Dispose()
        $subtitleFont.Dispose()
        $titleFont.Dispose()
        $eyebrowFont.Dispose()
        $accentPen.Dispose()
        $linePen.Dispose()
        $whiteBrush.Dispose()
        $mutedBrush.Dispose()
        $inkBrush.Dispose()
        $accentSoftBrush.Dispose()
        $accentBrush.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$covers = @(
    @{ File = 'cover-01-daily-life.png'; Title = '처음 만나는 한국 생활'; Subtitle = '일상 속 듣기·읽기·쓰기·말하기'; Accent = '#4F7DF3' },
    @{ File = 'cover-02-campus.png'; Title = '대학 생활 완전 정복'; Subtitle = '수업, 동아리, 친구와 함께하는 한국어'; Accent = '#7C5CE7' },
    @{ File = 'cover-03-travel.png'; Title = '한국 여행 실전 한국어'; Subtitle = '교통, 숙소, 관광 안내를 한 번에'; Accent = '#00A878' },
    @{ File = 'cover-04-shopping.png'; Title = '쇼핑과 서비스 한국어'; Subtitle = '주문부터 교환까지 자연스럽게'; Accent = '#E76F51' },
    @{ File = 'cover-05-health.png'; Title = '건강하고 안전한 생활'; Subtitle = '병원, 약국, 안전 안내 중심 훈련'; Accent = '#D94B70' },
    @{ File = 'cover-06-work.png'; Title = '취업과 직장 한국어'; Subtitle = '면접, 일정, 협업 상황 종합 연습'; Accent = '#3D5A80' },
    @{ File = 'cover-07-culture.png'; Title = '한국 문화 깊이 읽기'; Subtitle = '축제, 공연, 미디어를 통한 표현 확장'; Accent = '#F4A261' },
    @{ File = 'cover-08-environment.png'; Title = '환경과 우리 동네'; Subtitle = '생활 속 실천을 말하고 설득하기'; Accent = '#2A9D8F' },
    @{ File = 'cover-09-digital.png'; Title = '디지털 생활과 미디어'; Subtitle = '온라인 수업, 앱, 정보 활용 한국어'; Accent = '#5865F2' },
    @{ File = 'cover-10-community.png'; Title = '지역 사회와 공공 서비스'; Subtitle = '도서관, 주민 센터, 공공 안내 이해'; Accent = '#8E6C4F' },
    @{ File = 'cover-11-food.png'; Title = '한국 음식과 식생활'; Subtitle = '메뉴, 조리법, 식문화 종합 연습'; Accent = '#C44536' },
    @{ File = 'cover-12-future.png'; Title = '미래 계획과 자기 계발'; Subtitle = '목표를 설명하고 계획을 구체화하기'; Accent = '#5B8C5A' },
    @{ File = 'cover-13-social.png'; Title = '사회 이슈 토론 한국어'; Subtitle = '자료를 해석하고 근거 있게 말하기'; Accent = '#6D597A' }
)

for ($index = 0; $index -lt $covers.Count; $index++) {
    $cover = $covers[$index]
    New-ShowcaseBitmap `
        -Path (Join-Path $imageRoot $cover.File) `
        -Eyebrow ('VIP DEMO SET {0:D2}' -f ($index + 1)) `
        -Title $cover.Title `
        -Subtitle $cover.Subtitle `
        -AccentHex $cover.Accent `
        -Facts @('3개의 모의 테스트', '각 테스트 4개 영역', '자체 제작 문항과 상세 해설')
}

$stimuli = @(
    @{ File = 'stimulus-01-train.png'; Eye = '교통 안내'; Title = '주말 열차 시간표'; Sub = '서울역 → 바다역'; Accent = '#4F7DF3'; Facts = @('08:30 · 일반열차', '10:10 · 급행열차', '13:40 · 일반열차') },
    @{ File = 'stimulus-02-cafe.png'; Eye = '카페 메뉴'; Title = '오늘의 음료'; Sub = '오후 2시부터 할인'; Accent = '#E76F51'; Facts = @('아메리카노 3,500원', '유자차 4,000원', '딸기 주스 4,500원') },
    @{ File = 'stimulus-03-weather.png'; Eye = '주간 날씨'; Title = '이번 주 기온 변화'; Sub = '우산과 겉옷을 준비하세요'; Accent = '#00A878'; Facts = @('월요일 18°C · 맑음', '수요일 14°C · 비', '금요일 20°C · 구름') },
    @{ File = 'stimulus-04-library.png'; Eye = '도서관 공지'; Title = '운영 시간 변경'; Sub = '8월 1일부터 적용'; Accent = '#7C5CE7'; Facts = @('평일 08:00–20:00', '토요일 어린이 독서 모임', '반납 기계 24시간 이용') },
    @{ File = 'stimulus-05-recycle.png'; Eye = '환경 캠페인'; Title = '올바른 분리배출'; Sub = '깨끗한 동네를 함께 만들어요'; Accent = '#2A9D8F'; Facts = @('내용물을 비우기', '재질별로 나누기', '수요일 저녁에 배출') },
    @{ File = 'stimulus-06-festival.png'; Eye = '문화 행사'; Title = '한빛 가을 축제'; Sub = '시민 공원 중앙 무대'; Accent = '#F4A261'; Facts = @('14:00 전통 공연', '16:00 음식 체험', '19:00 야외 영화') },
    @{ File = 'stimulus-07-campus.png'; Eye = '캠퍼스 지도'; Title = '학생 지원 시설'; Sub = '필요한 장소를 찾아보세요'; Accent = '#3D5A80'; Facts = @('1층 학생 상담실', '2층 국제 교류실', '3층 조용한 학습실') },
    @{ File = 'stimulus-08-survey.png'; Eye = '설문 조사'; Title = '통학 방법 변화'; Sub = '2024년과 2026년 비교'; Accent = '#D94B70'; Facts = @('버스 45% → 35%', '자전거 20% → 35%', '승용차 10% → 5%') }
)

foreach ($stimulus in $stimuli) {
    New-ShowcaseBitmap `
        -Path (Join-Path $imageRoot $stimulus.File) `
        -Eyebrow $stimulus.Eye `
        -Title $stimulus.Title `
        -Subtitle $stimulus.Sub `
        -AccentHex $stimulus.Accent `
        -Facts $stimulus.Facts
}

$listeningClips = @(
    @{ File = 'listening-01-library.wav'; Text = '여: 민수 씨, 내일 도서관에서 같이 공부할까요? 남: 좋아요. 그런데 오전에는 아르바이트가 있어요. 여: 그럼 오후 두 시에 일 층 카페 앞에서 만나요. 남: 네, 제가 먼저 가서 자리를 잡을게요.' },
    @{ File = 'listening-02-subway.wav'; Text = '안내 말씀드립니다. 오늘 오후 세 시부터 시청역 공사로 이 번 출구를 이용할 수 없습니다. 버스를 타실 분은 사 번 출구 앞 정류장을 이용해 주십시오.' },
    @{ File = 'listening-03-hospital.wav'; Text = '여: 어제부터 목이 아프고 열도 조금 나요. 남: 오늘은 따뜻한 물을 많이 드시고 쉬세요. 열이 계속 나면 내일 오전에 다시 병원에 오십시오.' },
    @{ File = 'listening-04-delivery.wav'; Text = '남: 주문한 책이 아직 도착하지 않았어요. 여: 확인해 보니 주소에 아파트 동 번호가 빠져 있습니다. 남: 백이 동 오백삼 호입니다. 여: 네, 오늘 저녁에 다시 배송하겠습니다.' },
    @{ File = 'listening-05-festival.wav'; Text = '여: 이번 토요일에 한빛 축제에 갈래요? 남: 좋아요. 저는 전통 공연을 보고 싶어요. 여: 공연은 오후 두 시에 시작해요. 먼저 점심을 먹고 만나요.' },
    @{ File = 'listening-06-office.wav'; Text = '팀장: 금요일 회의 자료는 목요일 오후까지 공유해 주세요. 직원: 표와 그래프도 새로 만들까요? 팀장: 네, 지난달 자료와 비교할 수 있게 정리해 주세요.' },
    @{ File = 'listening-07-recycle.wav'; Text = '관리실에서 알려 드립니다. 이번 주 분리배출은 수요일 저녁 일곱 시부터 아홉 시까지입니다. 종이 상자는 접고, 플라스틱 용기는 깨끗이 씻어서 버려 주십시오.' },
    @{ File = 'listening-08-weather.wav'; Text = '내일은 오전에 맑겠지만 오후부터 비가 내리겠습니다. 낮 기온은 십사 도로 오늘보다 낮겠습니다. 외출하실 때 우산과 얇은 겉옷을 준비하십시오.' }
)

$speakingClips = @(
    @{ File = 'speaking-01-place.wav'; Text = '자신이 자주 가는 동네 장소를 소개하십시오. 어디인지, 무엇을 하는지, 좋아하는 이유를 말하십시오.' },
    @{ File = 'speaking-02-late.wav'; Text = '친구가 버스를 잘못 타서 약속 장소에 늦는다고 전화했습니다. 친구에게 길을 알려 주고 새 약속 시간을 제안하십시오.' },
    @{ File = 'speaking-03-service.wav'; Text = '온라인으로 산 물건에 문제가 있습니다. 고객 센터에 문제를 설명하고 원하는 해결 방법을 말하십시오.' },
    @{ File = 'speaking-04-campus.wav'; Text = '학교의 조용한 학습 공간을 늘리는 방안에 대해 찬성하거나 반대하는 입장을 말하고 두 가지 이유를 제시하십시오.' },
    @{ File = 'speaking-05-environment.wav'; Text = '우리 동네에서 일회용품 사용을 줄이기 위한 캠페인을 제안하십시오. 대상, 활동 방법, 기대 효과를 포함하십시오.' },
    @{ File = 'speaking-06-work.wav'; Text = '팀 일정이 갑자기 바뀌었습니다. 동료에게 변경 내용을 설명하고 업무를 다시 나누는 방법을 제안하십시오.' },
    @{ File = 'speaking-07-culture.wav'; Text = '외국인 친구에게 추천하고 싶은 한국 문화 행사를 소개하십시오. 행사 내용과 추천 이유를 구체적으로 말하십시오.' },
    @{ File = 'speaking-08-future.wav'; Text = '앞으로 일 년 동안 이루고 싶은 학습 목표를 말하고, 매달 실천할 계획과 어려움을 해결할 방법을 설명하십시오.' }
)

$synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $koreanVoice = $synthesizer.GetInstalledVoices() |
        Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'ko-KR' } |
        Select-Object -First 1
    if ($null -eq $koreanVoice) {
        throw 'Không tìm thấy giọng đọc ko-KR trong Windows.'
    }
    $synthesizer.SelectVoice($koreanVoice.VoiceInfo.Name)
    $synthesizer.Rate = -1
    $synthesizer.Volume = 100

    foreach ($clip in @($listeningClips + $speakingClips)) {
        $path = Join-Path $audioRoot $clip.File
        $stream = [System.IO.File]::Open(
            $path,
            [System.IO.FileMode]::Create,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None)
        try {
            $synthesizer.SetOutputToWaveStream($stream)
            $synthesizer.Speak($clip.Text)
            $synthesizer.SetOutputToNull()
        }
        finally {
            $stream.Dispose()
        }
    }
}
finally {
    $synthesizer.Dispose()
}

$manifest = [ordered]@{
    fixtureKey = 'ksh-practice-showcase-v1'
    generatedAt = [DateTimeOffset]::Now.ToString('o')
    provenance = 'KSH_ORIGINAL_DEMO'
    contentLicense = 'KSH internal demo content; original prompts and visuals'
    examMaterialPolicy = 'No official TOPIK question, image, or recording is embedded'
    imageCount = (Get-ChildItem -LiteralPath $imageRoot -Filter '*.png').Count
    audioCount = (Get-ChildItem -LiteralPath $audioRoot -Filter '*.wav').Count
    voice = $koreanVoice.VoiceInfo.Name
}
$manifest | ConvertTo-Json -Depth 4 |
    Set-Content -LiteralPath (Join-Path $assetRoot 'manifest.json') -Encoding utf8

Write-Host ("Generated {0} PNG and {1} WAV files in {2}" -f
    $manifest.imageCount, $manifest.audioCount, $assetRoot)

