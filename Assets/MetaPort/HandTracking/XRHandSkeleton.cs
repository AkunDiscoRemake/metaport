using UnityEngine;

namespace MetaPort.HandTracking
{
    public enum XRHandJoint
    {
        Palm = 0,
        Wrist = 1,
        ThumbMetacarpal = 2, ThumbProximal = 3, ThumbDistal = 4, ThumbTip = 5,
        IndexMetacarpal = 6, IndexProximal = 7, IndexIntermediate = 8, IndexDistal = 9, IndexTip = 10,
        MiddleMetacarpal = 11, MiddleProximal = 12, MiddleIntermediate = 13, MiddleDistal = 14, MiddleTip = 15,
        RingMetacarpal = 16, RingProximal = 17, RingIntermediate = 18, RingDistal = 19, RingTip = 20,
        LittleMetacarpal = 21, LittleProximal = 22, LittleIntermediate = 23, LittleDistal = 24, LittleTip = 25
    }

    public struct XRJoint
    {
        public Vector3 position;
        public Quaternion rotation;
        public float confidence;
        public bool isTracked;
    }

    public class XRHandSkeleton
    {
        public XRJoint[] joints = new XRJoint[26];
        public Vector3 palmPosition;
        public Quaternion palmRotation;
        public Core.MonadoOpenXRLoader.Handedness handedness;

        public XRHandSkeleton(Core.MonadoOpenXRLoader.Handedness hand)
        {
            handedness = hand;
        }

        public void UpdateFromMediaPipe(MediaPipeHand hand)
        {
            palmPosition = hand.palmPosition;
            // Map 21 MediaPipe landmarks to 26 OpenXR joints
            // MediaPipe: 0 wrist, 1-4 thumb, 5-8 index, 9-12 middle, 13-16 ring, 17-20 pinky
            if (hand.landmarks == null || hand.landmarks.Length < 21) return;

            // Wrist
            joints[(int)XRHandJoint.Wrist].position = hand.landmarks[0];
            joints[(int)XRHandJoint.Palm].position = (hand.landmarks[0] + hand.landmarks[5] + hand.landmarks[17]) / 3f;
            palmPosition = joints[(int)XRHandJoint.Palm].position;

            // Thumb
            joints[(int)XRHandJoint.ThumbMetacarpal].position = hand.landmarks[1];
            joints[(int)XRHandJoint.ThumbProximal].position = hand.landmarks[2];
            joints[(int)XRHandJoint.ThumbDistal].position = hand.landmarks[3];
            joints[(int)XRHandJoint.ThumbTip].position = hand.landmarks[4];

            // Index
            joints[(int)XRHandJoint.IndexMetacarpal].position = hand.landmarks[5];
            joints[(int)XRHandJoint.IndexProximal].position = hand.landmarks[6];
            joints[(int)XRHandJoint.IndexIntermediate].position = hand.landmarks[7];
            joints[(int)XRHandJoint.IndexTip].position = hand.landmarks[8];

            // Middle
            joints[(int)XRHandJoint.MiddleMetacarpal].position = hand.landmarks[9];
            joints[(int)XRHandJoint.MiddleProximal].position = hand.landmarks[10];
            joints[(int)XRHandJoint.MiddleIntermediate].position = hand.landmarks[11];
            joints[(int)XRHandJoint.MiddleTip].position = hand.landmarks[12];

            // Ring
            joints[(int)XRHandJoint.RingMetacarpal].position = hand.landmarks[13];
            joints[(int)XRHandJoint.RingProximal].position = hand.landmarks[14];
            joints[(int)XRHandJoint.RingIntermediate].position = hand.landmarks[15];
            joints[(int)XRHandJoint.RingTip].position = hand.landmarks[16];

            // Little
            joints[(int)XRHandJoint.LittleMetacarpal].position = hand.landmarks[17];
            joints[(int)XRHandJoint.LittleProximal].position = hand.landmarks[18];
            joints[(int)XRHandJoint.LittleIntermediate].position = hand.landmarks[19];
            joints[(int)XRHandJoint.LittleTip].position = hand.landmarks[20];

            // Calculate rotations
            for (int i = 0; i < joints.Length; i++)
            {
                joints[i].isTracked = true;
                joints[i].confidence = hand.confidence;
            }
        }

        public XRJoint GetJoint(XRHandJoint joint) => joints[(int)joint];
        public Vector3 GetJointDirection(XRHandJoint joint)
        {
            // Approximate direction from joint to tip
            return (joints[(int)XRHandJoint.IndexTip].position - joints[(int)XRHandJoint.IndexProximal].position).normalized;
        }

        public Vector3 GetPinchPosition()
        {
            var thumb = joints[(int)XRHandJoint.ThumbTip].position;
            var index = joints[(int)XRHandJoint.IndexTip].position;
            return (thumb + index) * 0.5f;
        }

        public Quaternion GetPinchRotation() => Quaternion.LookRotation(GetJointDirection(XRHandJoint.IndexTip));

        public float GetPinchStrength()
        {
            float dist = Vector3.Distance(joints[(int)XRHandJoint.ThumbTip].position, joints[(int)XRHandJoint.IndexTip].position);
            return Mathf.Clamp01(1f - dist / 0.08f);
        }
    }

    public class GestureRecognizer
    {
        public GestureType Recognize(XRHandSkeleton skeleton)
        {
            float pinch = skeleton.GetPinchStrength();
            if (pinch > 0.8f) return GestureType.Pinch;

            // Check finger extensions
            bool indexExtended = IsFingerExtended(skeleton, XRHandJoint.IndexProximal, XRHandJoint.IndexTip);
            bool middleExtended = IsFingerExtended(skeleton, XRHandJoint.MiddleProximal, XRHandJoint.MiddleTip);
            bool ringExtended = IsFingerExtended(skeleton, XRHandJoint.RingProximal, XRHandJoint.RingTip);
            bool littleExtended = IsFingerExtended(skeleton, XRHandJoint.LittleProximal, XRHandJoint.LittleTip);
            bool thumbExtended = IsFingerExtended(skeleton, XRHandJoint.ThumbProximal, XRHandJoint.ThumbTip);

            if (indexExtended && !middleExtended && !ringExtended && !littleExtended) return GestureType.Point;
            if (indexExtended && middleExtended && ringExtended && littleExtended && thumbExtended) return GestureType.OpenPalm;
            if (!indexExtended && !middleExtended && !ringExtended && !littleExtended) return GestureType.Fist;
            if (thumbExtended && !indexExtended && !middleExtended) return GestureType.ThumbsUp;

            return GestureType.None;
        }

        bool IsFingerExtended(XRHandSkeleton s, XRHandJoint proximal, XRHandJoint tip)
        {
            var palm = s.palmPosition;
            var prox = s.GetJoint(proximal).position;
            var t = s.GetJoint(tip).position;
            float distPalmTip = Vector3.Distance(palm, t);
            float distPalmProx = Vector3.Distance(palm, prox);
            return distPalmTip > distPalmProx * 1.3f;
        }
    }
}
