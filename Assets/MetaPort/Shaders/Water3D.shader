Shader "MetaPort/Water3D" {
Properties { _BaseColor ("Color", Color)=(0.2,0.5,0.8,0.8) _WaveSpeed ("Wave Speed", Float)=0.5 }
SubShader {
Tags { "Queue"="Transparent" "RenderType"="Transparent" }
Blend SrcAlpha OneMinusSrcAlpha
Pass {
CGPROGRAM
#pragma vertex vert
#pragma fragment frag
#include "UnityCG.cginc"
struct appdata { float4 vertex:POSITION; float2 uv:TEXCOORD0; };
struct v2f { float4 pos:SV_POSITION; float2 uv:TEXCOORD0; };
float4 _BaseColor; float _WaveSpeed;
v2f vert(appdata v){ v2f o; v.vertex.y+=sin(v.uv.x*10+_Time.y*_WaveSpeed)*0.05; o.pos=UnityObjectToClipPos(v.vertex); o.uv=v.uv; return o; }
fixed4 frag(v2f i):SV_Target { return _BaseColor; }
ENDCG
}
}
}
