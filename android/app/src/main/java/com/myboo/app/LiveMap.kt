package com.myboo.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

private class FitState { var fitted = false; var lastRequest = 0 }

/** Live OpenStreetMap view of the circle (no API key), tinted to match the notebook theme. */
@Composable
fun LiveMap(members: List<Member>, places: List<PlaceInfo>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val nb = MaterialTheme.notebook
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fit = remember { FitState() }
    var fitRequest by remember { mutableIntStateOf(0) }

    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName // required by the OSM tile usage policy
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isTilesScaledToDpi = true
            controller.setZoom(14.0)
            // Let the map take drags/pinches instead of the surrounding list.
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    Box(modifier.sticker(nb.sky, radius = 24.dp, shadow = 6.dp, border = 2.5.dp)) {
        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxSize(),
            update = { m ->
                m.overlayManager.tilesOverlay.setColorFilter(tileFilter(nb.dark))
                m.overlays.clear()
                val ink = nb.ink.toArgb()

                places.forEach { p ->
                    val poly = Polygon(m).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.lng), p.radiusM)
                        fillPaint.color = nb.mint.copy(alpha = 0.55f).toArgb()
                        outlinePaint.color = ink
                        outlinePaint.strokeWidth = 3f
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                        title = p.name
                    }
                    m.overlays.add(poly)
                    m.overlays.add(Marker(m).apply {
                        position = GeoPoint(p.lat, p.lng)
                        icon = placeLabel(ctx, p.name, nb.mint.toArgb(), ink)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                    })
                }

                val located = members.filter { it.lat != null && it.lng != null }
                located.forEach { mem ->
                    m.overlays.add(Marker(m).apply {
                        position = GeoPoint(mem.lat!!, mem.lng!!)
                        icon = avatarPin(ctx, mem.name, avatarColor(mem.id).toArgb(), ink, mem.driving)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = mem.name + (if (mem.driving) " · driving" else "")
                        snippet = "Updated ${ago(mem.ts)}"
                    })
                }

                val pts = located.map { GeoPoint(it.lat!!, it.lng!!) } + places.map { GeoPoint(it.lat, it.lng) }
                if (pts.isNotEmpty() && (!fit.fitted || fit.lastRequest != fitRequest)) {
                    fit.fitted = true
                    fit.lastRequest = fitRequest
                    m.doOnLayout { fitTo(m, pts) }
                }
                m.invalidate()
            },
        )

        // Required OSM attribution + recenter control.
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.bodySmall,
            color = nb.ink.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                .sticker(nb.paper.copy(alpha = 0.9f), radius = 8.dp, shadow = 0.dp, border = 1.dp)
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Text(
            "recenter",
            style = MaterialTheme.typography.bodySmall,
            color = nb.ink,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .sticker(nb.butter, radius = 10.dp, shadow = 2.dp, border = 1.5.dp)
                .clickable { fitRequest++ }
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private fun fitTo(m: MapView, pts: List<GeoPoint>) {
    if (pts.size == 1) {
        m.controller.setZoom(16.0)
        m.controller.setCenter(pts[0])
        return
    }
    var box = BoundingBox.fromGeoPointsSafe(pts)
    val pad = 0.0012 // ~130 m: keeps a tight group from over-zooming
    if (box.latitudeSpan < pad * 2 || box.longitudeSpan < pad * 2) {
        val c = box.centerWithDateLine
        box = BoundingBox(c.latitude + pad, c.longitude + pad, c.latitude - pad, c.longitude - pad)
    }
    m.zoomToBoundingBox(box.increaseByScale(1.5f), false)
}

/** Pastel-wash the OSM tiles (light) or soften an inverted dark map (dark). */
private fun tileFilter(dark: Boolean): ColorMatrixColorFilter {
    val m = ColorMatrix()
    if (dark) {
        m.set(floatArrayOf(
            -0.78f, 0f, 0f, 0f, 230f,
            0f, -0.78f, 0f, 0f, 226f,
            0f, 0f, -0.78f, 0f, 238f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val sat = ColorMatrix().apply { setSaturation(0.5f) }
        m.postConcat(sat)
    } else {
        m.setSaturation(0.5f)
        m.postConcat(ColorMatrix(floatArrayOf(
            0.78f, 0f, 0f, 0f, 46f,
            0f, 0.78f, 0f, 0f, 42f,
            0f, 0f, 0.78f, 0f, 38f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    return ColorMatrixColorFilter(m)
}

// ---------- marker bitmaps (drawn on a Canvas so they match the notebook style) ----------

private fun hand(ctx: Context, bold: Boolean): Typeface =
    ResourcesCompat.getFont(ctx, R.font.patrick_hand) ?: Typeface.DEFAULT.let { if (bold) Typeface.DEFAULT_BOLD else it }

private fun avatarPin(ctx: Context, name: String, fill: Int, ink: Int, driving: Boolean): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (60 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val r = 20 * d
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (driving) { // dashed halo = "on the move"
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 2 * d
        p.pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
        c.drawCircle(cx, cx, r + 7 * d, p)
        p.pathEffect = null
    }
    p.style = Paint.Style.FILL; p.color = ink and 0x00FFFFFF or 0x55000000 // hard shadow
    c.drawCircle(cx + 2.5f * d, cx + 3f * d, r, p)
    p.color = fill; c.drawCircle(cx, cx, r, p)
    p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 2.5f * d; c.drawCircle(cx, cx, r, p)
    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3B3A4A.toInt(); textSize = 24 * d; textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(ctx, R.font.caveat) ?: Typeface.DEFAULT_BOLD; isFakeBoldText = true
    }
    c.drawText(name.firstOrNull()?.uppercase() ?: "?", cx, cx + 8 * d, t)
    return BitmapDrawable(ctx.resources, bmp)
}

private fun placeLabel(ctx: Context, name: String, fill: Int, ink: Int): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B3A4A.toInt(); textSize = 15 * d; typeface = hand(ctx, false) }
    val w = (t.measureText(name) + 18 * d).toInt()
    val h = (26 * d).toInt()
    val bmp = Bitmap.createBitmap(w + (4 * d).toInt(), h + (4 * d).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(1 * d, 1 * d, w.toFloat(), h.toFloat())
    p.color = ink and 0x00FFFFFF or 0x55000000
    c.drawRoundRect(RectF(rect).apply { offset(2.5f * d, 2.5f * d) }, 8 * d, 8 * d, p)
    p.color = fill; c.drawRoundRect(rect, 8 * d, 8 * d, p)
    p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 1.8f * d; c.drawRoundRect(rect, 8 * d, 8 * d, p)
    c.drawText(name, 10 * d, 18 * d, t)
    return BitmapDrawable(ctx.resources, bmp)
}
