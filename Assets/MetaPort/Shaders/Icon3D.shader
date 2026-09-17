Shader "MetaPort/Icon3D"
{
    Properties
    {
        _BaseColor ("Base Color", Color) = (0.3,0.5,0.9,1)
        _Metallic ("Metallic", Range(0,1)) = 0.1
        _Smoothness ("Smoothness", Range(0,1)) = 0.85
        _Emission ("Emission", Color) = (0,0,0,0)
        _DepthFactor ("Depth Factor", Float) = 1
    }
    SubShader
    {
        Tags { "RenderType"="Opaque" "Queue"="Geometry" }
        LOD 300
        Pass
        {
            Name "ForwardLit"
            Tags { "LightMode"="UniversalForward" }
            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Lighting.hlsl"

            struct Attributes { float4 positionOS:POSITION; float3 normalOS:NORMAL; float2 uv:TEXCOORD0; };
            struct Varyings { float4 positionHCS:SV_POSITION; float3 positionWS:TEXCOORD0; float3 normalWS:TEXCOORD1; float2 uv:TEXCOORD2; };

            CBUFFER_START(UnityPerMaterial)
                float4 _BaseColor; float _Metallic; float _Smoothness; float4 _Emission; float _DepthFactor;
            CBUFFER_END

            Varyings vert(Attributes IN){ Varyings OUT; OUT.positionWS=TransformObjectToWorld(IN.positionOS.xyz); OUT.positionHCS=TransformWorldToHClip(OUT.positionWS); OUT.normalWS=TransformObjectToWorldNormal(IN.normalOS); OUT.uv=IN.uv; return OUT; }
            half4 frag(Varyings IN):SV_Target
            {
                InputData lightingInput = (InputData)0;
                lightingInput.positionWS = IN.positionWS;
                lightingInput.normalWS = normalize(IN.normalWS);
                lightingInput.viewDirectionWS = normalize(_WorldSpaceCameraPos - IN.positionWS);
                lightingInput.shadowCoord = TransformWorldToShadowCoord(IN.positionWS);

                SurfaceData surface;
                surface.albedo = _BaseColor.rgb;
                surface.metallic = _Metallic;
                surface.smoothness = _Smoothness;
                surface.normalTS = half3(0,0,1);
                surface.emission = _Emission.rgb;
                surface.occlusion = 1;
                surface.alpha = 1;
                surface.specular = 0;
                surface.clearCoatMask = 0;
                surface.clearCoatSmoothness = 0;

                half4 color = UniversalFragmentPBR(lightingInput, surface);
                // Add depth gradient
                color.rgb += IN.uv.y * 0.05 * _DepthFactor;
                return color;
            }
            ENDHLSL
        }
    }
}
