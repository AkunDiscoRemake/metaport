Shader "MetaPort/SpatialWindow"
{
    Properties
    {
        _BaseColor ("Base Color", Color) = (0.12,0.12,0.14,0.92)
        _CornerRadius ("Corner Radius", Float) = 0.08
        _Depth ("Depth", Float) = 0.08
        _Metallic ("Metallic", Range(0,1)) = 0.1
        _Smoothness ("Smoothness", Range(0,1)) = 0.8
        _Opacity ("Opacity", Range(0,1)) = 0.92
        _HoverIntensity ("Hover Intensity", Float) = 0
        _EmissionColor ("Emission", Color) = (0,0,0,0)
        _MainTex ("Content Tex", 2D) = "white" {}
    }
    SubShader
    {
        Tags { "RenderType"="Transparent" "Queue"="Transparent" "RenderPipeline"="UniversalPipeline" }
        LOD 300
        Blend SrcAlpha OneMinusSrcAlpha
        ZWrite Off
        Cull Off

        Pass
        {
            Name "ForwardLit"
            Tags { "LightMode"="UniversalForward" }

            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Lighting.hlsl"

            struct Attributes
            {
                float4 positionOS : POSITION;
                float3 normalOS : NORMAL;
                float2 uv : TEXCOORD0;
            };
            struct Varyings
            {
                float4 positionHCS : SV_POSITION;
                float3 positionWS : TEXCOORD0;
                float3 normalWS : TEXCOORD1;
                float2 uv : TEXCOORD2;
            };

            CBUFFER_START(UnityPerMaterial)
                float4 _BaseColor;
                float _CornerRadius;
                float _Depth;
                float _Metallic;
                float _Smoothness;
                float _Opacity;
                float _HoverIntensity;
                float4 _EmissionColor;
            CBUFFER_END
            TEXTURE2D(_MainTex); SAMPLER(sampler_MainTex);

            Varyings vert(Attributes IN)
            {
                Varyings OUT;
                OUT.positionWS = TransformObjectToWorld(IN.positionOS.xyz);
                OUT.positionHCS = TransformWorldToHClip(OUT.positionWS);
                OUT.normalWS = TransformObjectToWorldNormal(IN.normalOS);
                OUT.uv = IN.uv;
                return OUT;
            }

            half4 frag(Varyings IN) : SV_Target
            {
                // Rounded corners SDF
                float2 uv = IN.uv * 2 - 1;
                float2 size = float2(1,1);
                float2 d = abs(uv) - size + _CornerRadius;
                float dist = length(max(d,0)) + min(max(d.x,d.y),0) - _CornerRadius;
                float alpha = 1 - smoothstep(-0.01, 0.01, dist);

                // Glass morphism + blur approximation
                half4 baseCol = _BaseColor;
                baseCol.a *= _Opacity * alpha;

                // Hover glow
                baseCol.rgb += _HoverIntensity * 0.3;

                // Fresnel for depth perception - 3D spatial
                float3 viewDir = normalize(_WorldSpaceCameraPos - IN.positionWS);
                float fresnel = pow(1 - saturate(dot(viewDir, IN.normalWS)), 3);
                baseCol.rgb += fresnel * 0.15;

                // Content texture if present
                half4 content = SAMPLE_TEXTURE2D(_MainTex, sampler_MainTex, IN.uv);
                baseCol.rgb = lerp(baseCol.rgb, content.rgb, content.a * 0.9);

                baseCol.rgb += _EmissionColor.rgb;

                return baseCol;
            }
            ENDHLSL
        }
    }
    FallBack "Universal Render Pipeline/Lit"
}
