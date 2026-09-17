using UnityEngine;

namespace MetaPort.UI.SpatialUI
{
    public class HandInteraction : MonoBehaviour
    {
        public float rayLength = 10f;
        public LineRenderer pointerLine;
        public Transform pointerDot;

        void Awake()
        {
            if (pointerLine == null)
            {
                var go = new GameObject("PointerLine");
                go.transform.SetParent(transform,false);
                pointerLine = go.AddComponent<LineRenderer>();
                pointerLine.startWidth = 0.005f;
                pointerLine.endWidth = 0.002f;
                pointerLine.material = new Material(Shader.Find("Unlit/Color"));
                pointerLine.material.color = Color.white;
            }
        }

        void Update()
        {
            var handManager = HandTracking.HandTrackingManager.Instance;
            if (handManager == null) return;

            if (handManager.IsTracking(Core.MonadoOpenXRLoader.Handedness.Right))
            {
                var skeleton = handManager.GetSkeleton(Core.MonadoOpenXRLoader.Handedness.Right);
                var indexTip = skeleton.GetJoint(HandTracking.XRHandJoint.IndexTip).position;
                var dir = skeleton.GetJointDirection(HandTracking.XRHandJoint.IndexTip);

                pointerLine.SetPosition(0, indexTip);
                if (Physics.Raycast(indexTip, dir, out var hit, rayLength))
                {
                    pointerLine.SetPosition(1, hit.point);
                    if (pointerDot != null) pointerDot.position = hit.point;
                }
                else
                {
                    pointerLine.SetPosition(1, indexTip + dir * rayLength);
                }
            }
        }
    }
}
