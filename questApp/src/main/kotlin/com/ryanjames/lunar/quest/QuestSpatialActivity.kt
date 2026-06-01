package com.ryanjames.lunar.quest

import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.VrInputSystemType

class QuestSpatialActivity : AppSystemActivity() {
    override fun registerFeatures(): List<SpatialFeature> =
        listOf(
            VRFeature(
                activity = this,
                inputSystemType = VrInputSystemType.SIMPLE_CONTROLLER,
            ),
        )

    override fun onSceneReady() {
        super.onSceneReady()

        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.setViewOrigin(0.0f, 0.0f, 0.0f, 0.0f)
        scene.setLightingEnvironment(
            ambientColor = Vector3(0.04f, 0.04f, 0.04f),
            sunColor = Vector3(1.0f, 1.0f, 1.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.15f,
        )
    }
}
