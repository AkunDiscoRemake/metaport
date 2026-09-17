Shader "MetaPort/HandOutline" {
Properties { _Color ("Color", Color) = (0.8,0.9,1,0.6) _OutlineWidth ("Width", Float)=0.005 }
SubShader {
Tags { "Queue"="Transparent" "RenderType"="Transparent" }
Pass {
Blend SrcAlpha OneMinusSrcAlpha ZWrite Off Cull Off
CGPROGRAM
#pragma vertex vert
#pragma fragment frag
#include "UnityCG.cginc"
struct appdata { float4 vertex:POSITION; float3 normal:NORMAL; };
struct v2f { float4 pos:SV_POSITION; };
float4 _Color; float _OutlineWidth;
v2f vert(appdata v){ v2f o; v.vertex.xyz+=v.normal*_OutlineWidth; o.pos=UnityObjectToClipPos(v.vertex); return o; }
fixed4 frag(v2f i):SV_Target { return _Color; }
ENDCG
}
}
}
