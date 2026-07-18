package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiSearch
import androidx.compose.ui.res.stringResource
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.GithubRelease
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.data.model.AppMapType
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import androidx.compose.material.icons.rounded.Layers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.BuildConfig
import androidx.compose.runtime.Composable
import com.suseoaa.locationspoofer.ui.theme.*

@Composable
fun HomeSearchBar(
    query: String,
    searchMode: com.suseoaa.locationspoofer.data.model.SearchMode,
    onSearchModeChange: (com.suseoaa.locationspoofer.data.model.SearchMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(4.dp, RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Search,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索地点或坐标...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        )
        Spacer(Modifier.width(10.dp))
        FilledIconButton(
            onClick = onSearch,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentBlue)
        ) {
            Icon(
                Icons.Rounded.Search,
                stringResource(R.string.search),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private var cachedPlacesClient: com.google.android.libraries.places.api.net.PlacesClient? = null

fun performPoiSearch(
    context: android.content.Context,
    activeEngine: com.suseoaa.locationspoofer.data.model.MapEngine,
    keyword: String,
    isDomestic: Boolean,
    onResult: (List<AppPoiItem>) -> Unit
) {
    if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.BAIDU) {
        try {
            val mPoiSearch = com.baidu.mapapi.search.poi.PoiSearch.newInstance()
            mPoiSearch.setOnGetPoiSearchResultListener(object :
                com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener {
                override fun onGetPoiResult(result: com.baidu.mapapi.search.poi.PoiResult?) {
                    if (result == null || result.error != com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ERROR) {
                        onResult(emptyList())
                        mPoiSearch.destroy()
                        return
                    }
                    val items = result.allPoi?.map {
                        AppPoiItem(
                            it.name ?: "",
                            it.address ?: "",
                            it.location.latitude,
                            it.location.longitude
                        )
                    } ?: emptyList()
                    onResult(items)
                    mPoiSearch.destroy()
                }

                override fun onGetPoiDetailResult(p0: com.baidu.mapapi.search.poi.PoiDetailResult?) {}
                override fun onGetPoiDetailResult(p0: com.baidu.mapapi.search.poi.PoiDetailSearchResult?) {}
                override fun onGetPoiIndoorResult(p0: com.baidu.mapapi.search.poi.PoiIndoorResult?) {}
            })
            val option = com.baidu.mapapi.search.poi.PoiCitySearchOption()
                .city("全国")
                .keyword(keyword)
                .pageNum(0)
                .pageCapacity(20)
            mPoiSearch.searchInCity(option)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(emptyList())
        }
    } else if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.AMAP) {
        try {
            val query = PoiSearch.Query(keyword, "", "")
            query.pageSize = 10
            query.pageNum = 0
            val search = PoiSearch(context, query)
            search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(
                    result: com.amap.api.services.poisearch.PoiResult?,
                    rCode: Int
                ) {
                    if (rCode == 1000 && result != null) {
                        onResult(result.pois?.map {
                            AppPoiItem(
                                it.title ?: "",
                                it.snippet ?: "",
                                it.latLonPoint.latitude,
                                it.latLonPoint.longitude
                            )
                        } ?: emptyList())
                    } else {
                        if (rCode == 10003 || rCode == 10012 || rCode == 10013 || rCode == 10014 || rCode == 1800 || rCode == 18000) {
                            android.widget.Toast.makeText(
                                context,
                                "高德搜索API调用失败(可能是额度耗尽)，请检查控制台或更换Key！",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else if (rCode != 1000) {
                            android.widget.Toast.makeText(
                                context,
                                "高德搜索失败(错误码:$rCode)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        onResult(emptyList())
                    }
                }

                override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
            })
            search.searchPOIAsyn()
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: ""
            if (e is com.amap.api.services.core.AMapException || msg.contains(
                    "limit",
                    ignoreCase = true
                ) || msg.contains("额度")
            ) {
                android.widget.Toast.makeText(
                    context,
                    "高德搜索异常(可能是额度耗尽)：$msg",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            onResult(emptyList())
        }
    } else {
        try {
            val placesClient =
                cachedPlacesClient ?: com.google.android.libraries.places.api.Places.createClient(
                    context.applicationContext
                ).also {
                    cachedPlacesClient = it
                }
            // 使用 Autocomplete 接口：全球关键词搜索，不限制国家/地区
            // 通过 sessionToken 分组请求，避免重复计费；不设置 country 限制以支持全球搜索
            val sessionToken =
                com.google.android.libraries.places.api.model.AutocompleteSessionToken.newInstance()
            // 设置覆盖全球的矩形偏移，阻止服务器根据 IP 推断区域，实现真正全球搜索
            val worldBounds =
                com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                    com.google.android.gms.maps.model.LatLng(-90.0, -180.0),
                    com.google.android.gms.maps.model.LatLng(90.0, 180.0)
                )
            val autocompleteRequest =
                com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                    .setQuery(keyword)
                    .setLocationBias(worldBounds)
                    .setSessionToken(sessionToken)
                    .build()

            placesClient.findAutocompletePredictions(autocompleteRequest)
                .addOnSuccessListener { autocompleteResponse ->
                    val predictions = autocompleteResponse.autocompletePredictions
                    if (predictions.isEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "No predictions found for: $keyword",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }
                    // 批量获取前5条预测结果的详情（坐标）
                    val fetchFields = listOf(
                        com.google.android.libraries.places.api.model.Place.Field.ID,
                        com.google.android.libraries.places.api.model.Place.Field.NAME,
                        com.google.android.libraries.places.api.model.Place.Field.LAT_LNG,
                        com.google.android.libraries.places.api.model.Place.Field.ADDRESS
                    )
                    val resultList = mutableListOf<AppPoiItem>()
                    val topPredictions = predictions.take(5)
                    var completedCount = 0
                    topPredictions.forEach { prediction ->
                        val fetchRequest =
                            com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(
                                prediction.placeId,
                                fetchFields
                            )
                        placesClient.fetchPlace(fetchRequest)
                            .addOnSuccessListener { fetchResponse ->
                                val place = fetchResponse.place
                                val latLng = place.latLng
                                if (latLng != null) {
                                    resultList.add(
                                        AppPoiItem(
                                            title = place.name ?: prediction.getPrimaryText(null)
                                                .toString(),
                                            snippet = place.address ?: prediction.getSecondaryText(
                                                null
                                            ).toString(),
                                            lat = latLng.latitude,
                                            lng = latLng.longitude
                                        )
                                    )
                                }
                            }
                            .addOnCompleteListener {
                                completedCount++
                                if (completedCount == topPredictions.size) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Search Success: ${resultList.size} results",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onResult(resultList)
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    android.widget.Toast.makeText(
                        context,
                        "Search Error: ${exception.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Search Catch Error: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            onResult(emptyList())
        }
    }
}