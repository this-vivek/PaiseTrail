package com.paisetrail.app.ui.components

import com.google.android.gms.maps.model.MapStyleOptions

/** A single custom dark map style used everywhere the app shows a map, regardless of the
 * device's light/dark theme — a consistent branded look (closer to Snapchat/Uber's map treatment
 * than default Google Maps) beats a map that reflows tile colors when the label chrome around it
 * switches theme. Deliberately quiet: no POI/transit clutter, muted road/water tones close to the
 * app's own ink/bg palette so pins (the actual data) are what stands out. */
private const val STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#1c1f24"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8a8d93"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0e0f11"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a3d44"}]},
  {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#26282c"}]},
  {"featureType":"road","elementType":"labels","stylers":[{"visibility":"off"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#2b2e34"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#33363c"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#12151c"}]}
]
"""

fun paisaMapStyle(): MapStyleOptions = MapStyleOptions(STYLE_JSON)
