using UnityEngine;

namespace MetaPort.Games.FishingVR
{
    public class FishingManager : MonoBehaviour
    {
        public Transform rod;
        public Transform water;
        public Transform fish;
        private bool _isFishing = false;
        private float _fishTimer = 0f;

        void Awake()
        {
            // Water plane 3D
            if (water == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Plane);
                go.name = "Water_3D";
                go.transform.SetParent(transform, false);
                go.transform.localPosition = Vector3.forward * 3f + Vector3.down * 0.5f;
                go.transform.localScale = Vector3.one * 2f;
                water = go.transform;
                var mat = new Material(Shader.Find("MetaPort/Water3D"));
                mat.SetColor("_BaseColor", new Color(0.2f,0.5f,0.8f,0.8f));
                go.GetComponent<Renderer>().material = mat;
            }
            if (rod == null)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                go.name = "FishingRod";
                go.transform.SetParent(transform, false);
                go.transform.localScale = new Vector3(0.02f,0.6f,0.02f);
                rod = go.transform;
            }
        }

        void Update()
        {
            var handManager = HandTracking.HandTrackingManager.Instance;
            if (handManager != null && handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right))
            {
                rod.position = handManager.GetHandPosition(Core.MonadoOpenXRLoader.Handedness.Right);
                var skel = handManager.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Right);
                rod.rotation = Quaternion.LookRotation(skel.GetJointDirection(HandTracking.XRHandJoint.IndexTip));
            }

            if (!_isFishing)
            {
                _fishTimer += Time.deltaTime;
                if (_fishTimer > 3f && Random.value > 0.98f)
                {
                    _isFishing = true;
                    Debug.Log("[FishingVR] Fish biting!");
                }
            }
        }
    }
}
