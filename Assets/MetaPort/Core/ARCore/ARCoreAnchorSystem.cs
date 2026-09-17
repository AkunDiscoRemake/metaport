using UnityEngine;
using UnityEngine.XR.ARFoundation;

namespace MetaPort.Core
{
    public class ARCoreAnchorSystem : MonoBehaviour
    {
        public ARAnchorManager anchorManager;
        void Awake(){ if(anchorManager==null) anchorManager=FindObjectOfType<ARAnchorManager>(); }
        public ARAnchor CreateAnchor(Vector3 pos, Quaternion rot)
        {
            var go = new GameObject("Anchor_3D");
            go.transform.position = pos;
            go.transform.rotation = rot;
            return go.AddComponent<ARAnchor>();
        }
    }
}
