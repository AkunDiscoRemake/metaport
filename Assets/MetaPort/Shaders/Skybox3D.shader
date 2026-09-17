Shader "MetaPort/Skybox3D" {
Properties { _TopColor ("Top", Color)=(0.3,0.7,1,1) _BottomColor ("Bottom", Color)=(0.9,0.7,0.4,1) }
SubShader {
Tags { "Queue"="Background" }
Pass {
CGPROGRAM
#pragma vertex vert
#pragma fragment frag
#include "UnityCG.cginc"
struct appdata { float4 vertex:POSITION; };
struct v2f { float4 pos:SV_POSITION; float3 worldPos:TEXCOORD0; };
float4 _TopColor; float4 _BottomColor;
v2f vert(appdata v){ v2f o; o.pos=UnityObjectToClipPos(v.vertex); o.worldPos=mul(unity_ObjectToWorld, v.vertex).xyz; return o; }
fixed4 frag(v2f i):SV_Target {
float t=saturate(i.worldPos.y*0.05+0.5);
return lerp(_BottomColor, _TopColor, t);
}
ENDCG
}
}
}
