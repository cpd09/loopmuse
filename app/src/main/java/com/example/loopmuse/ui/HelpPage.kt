package com.example.loopmuse.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.loopmuse.R

private data class HelpSection(
    val title: String,
    val description: String,
    @DrawableRes val screenshot: Int,
    val screenshotRatio: Float,
    val screenshotDescription: String
)

private val helpSections = listOf(
    HelpSection(
        "곡과 재생목록",
        "‘곡,폴더 선택’에서 음악 폴더를 고르고, 재생목록의 곡을 누르세요. 돋보기로 곡을 찾고 연필로 곡 추천·태그를 편집할 수 있습니다.",
        R.drawable.help_library, 1240f / 465f,
        "곡·폴더 선택 버튼과 재생목록 선택, 검색 및 곡 편집 버튼"
    ),
    HelpSection(
        "현재 재생곡",
        "위 막대는 재생 위치, 아래 막대는 음량입니다. 가운데에서 이전·재생·다음을 누르고, 가사 영역에서 가사를 확인하거나 다시 찾을 수 있습니다.",
        R.drawable.help_player, 1240f / 335f,
        "재생 위치와 음량 막대, 이전·재생·다음 및 맛보기 버튼"
    ),
    HelpSection(
        "알람",
        "홈의 시계 → 알람추가에서 시간·요일·곡을 정하고 저장하세요. 알람이 울릴 때 토끼를 잡으면 완전히 종료됩니다.",
        R.drawable.help_alarm, 1190f / 790f,
        "새 알람의 제목, 시간, 요일과 재생곡 설정 화면"
    ),
    HelpSection(
        "라운지",
        "게시글의 연필로 글을 남기세요. 앱 추천에서는 곡 제목을 눌러 들어보고 하트로 마음에 든 곡을 표시할 수 있습니다.",
        R.drawable.help_lounge, 1220f / 730f,
        "게시글과 앱 추천 탭, 추천곡 제목과 하트 버튼"
    ),
    HelpSection(
        "전체설정과 백업",
        "승인한 폴더에 사용 내용이 자동 보관됩니다. ‘백업 및 복원’에서 이전 파일을 복원하고, 알람과 재생목록 표시 방법을 조정하세요.",
        R.drawable.help_settings, 1220f / 1130f,
        "백업 및 복원 메뉴와 알람 공통 설정"
    )
)

@Composable
internal fun HelpPage(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("처음 시작", style = MaterialTheme.typography.titleMedium)
                Text(
                    "음악 폴더 선택 → 재생목록 선택 → 곡 재생. 필요한 기능은 아래 화면에서 찾아보세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(helpSections) { section ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.description, style = MaterialTheme.typography.bodyMedium)
                    val shape = RoundedCornerShape(10.dp)
                    Image(
                        painter = painterResource(section.screenshot),
                        contentDescription = section.screenshotDescription,
                        modifier = Modifier.fillMaxWidth().aspectRatio(section.screenshotRatio)
                            .clip(shape)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
        item {
            Text(
                "화면의 곡명과 설정값은 예시입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
