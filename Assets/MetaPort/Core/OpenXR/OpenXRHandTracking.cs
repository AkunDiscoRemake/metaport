using UnityEngine;

namespace MetaPort.Core.OpenXR
{
    public class OpenXRHandTracking : MonoBehaviour
    {
        public bool enableHandTracking = true;
        // Wrapper for OpenXR hand tracking extension XR_EXT_hand_tracking
        // When Monado available, uses native OpenXR
        // Else uses MediaPipe fallback

        void Update()
        {
            if (!enableHandTracking) return;
            // In real implementation, calls xrLocateHandJointsEXT
        }
    }
}
