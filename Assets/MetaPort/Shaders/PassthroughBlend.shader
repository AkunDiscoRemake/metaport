Shader "MetaPort/PassthroughBlend"
{
    Properties
    {
        _CameraTex ("Camera Tex", 2D) = "black" {}
        _Opacity ("Opacity", Range(0,1)) = 0.85
        _Blur ("Blur", Range(0,1)) = 0.1
        _Vignette ("Vignette", Range(0,1)) = 0.3
    }
    SubShader
    {
        Tags { "Queue"="Background" "RenderType"="Background" }
        Pass
        {
            ZWrite Off
            Cull Off
            Blend SrcAlpha OneMinusSrcAlpha

            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"

            struct Attributes { float4 positionOS:POSITION; float2 uv:TEXCOORD0; };
            struct Varyings { float4 positionHCS:SV_POSITION; float2 uv:TEXCOORD0; };

            TEXTURE2D(_CameraTex); SAMPLER(sampler_CameraTex);
            float _Opacity; float _Blur; float _Vignette;

            Varyings vert(Attributes IN){ Varyings OUT; OUT.positionHCS=TransformObjectToHClip(IN.positionOS.xyz); OUT.uv=IN.uv; return OUT; }
            half4 frag(Varyings IN):SV_Target
            {
                half4 cam = SAMPLE_TEXTURE2D(_CameraTex, sampler_CameraTex, IN.uv);
                // Vignette for comfort
                float2 uv = IN.uv*2-1;
                float vign = 1 - dot(uv,uv)*_Vignette*0.2;
                cam.rgb *= vign;
                cam.a = _Opacity;
                return cam;
            }
            ENDHLSL
        }
    }
}
