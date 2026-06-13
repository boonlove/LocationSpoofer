package com.suseoaa.locationspoofer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppMapType
import com.suseoaa.locationspoofer.ui.theme.AccentBlue

@Composable
fun MapTypeDialog(
    currentMapType: AppMapType,
    onMapTypeSelected: (AppMapType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.map_type_label),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapTypeItem(
                        title = stringResource(R.string.map_type_standard),
                        icon = Icons.Rounded.Map,
                        isSelected = currentMapType == AppMapType.NORMAL,
                        onClick = {
                            onMapTypeSelected(AppMapType.NORMAL)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    MapTypeItem(
                        title = stringResource(R.string.map_type_satellite),
                        icon = Icons.Rounded.Satellite,
                        isSelected = currentMapType == AppMapType.SATELLITE,
                        onClick = {
                            onMapTypeSelected(AppMapType.SATELLITE)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    MapTypeItem(
                        title = stringResource(R.string.map_type_3d),
                        icon = Icons.Rounded.ViewInAr,
                        isSelected = currentMapType == AppMapType.MAP_3D,
                        onClick = {
                            onMapTypeSelected(AppMapType.MAP_3D)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun MapTypeItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) AccentBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val containerColor = if (isSelected) AccentBlue.copy(alpha = 0.08f) else Color.Transparent
    val contentColor = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}
