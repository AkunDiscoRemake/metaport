using UnityEngine;
using UnityEditor;
using System.IO;

namespace MetaPort.Editor
{
    /// <summary>
    /// Auto-fix manifest on import - para preguiçosos
    /// Roda automaticamente quando importa o package e corrige tudo
    /// </summary>
    [InitializeOnLoad]
    public class AutoFixManifest
    {
        static AutoFixManifest()
        {
            EditorApplication.delayCall += FixManifest;
        }

        static void FixManifest()
        {
            string manifestPath = Path.Combine(Application.dataPath, "../Packages/manifest.json");
            if (!File.Exists(manifestPath)) return;

            string content = File.ReadAllText(manifestPath);
            
            bool needsFix = content.Contains("com.unity.modules.ar") || 
                           content.Contains("com.google.xr.cardboard") && content.Contains("1.12.0") ||
                           !content.Contains("OpenUPM");

            if (!needsFix) return;

            Debug.Log("[MetaPort] Detectado manifest com erro, corrigindo automaticamente para preguiçosos...");

            string fixedManifest = @"{
  ""dependencies"": {
    ""com.unity.xr.arfoundation"": ""5.1.2"",
    ""com.unity.xr.arcore"": ""5.1.2"",
    ""com.unity.xr.openxr"": ""1.9.1"",
    ""com.unity.xr.management"": ""4.4.0"",
    ""com.unity.xr.legacyinputhelpers"": ""2.1.10"",
    ""com.unity.render-pipelines.universal"": ""14.0.11"",
    ""com.unity.textmeshpro"": ""3.0.6"",
    ""com.unity.timeline"": ""1.7.6"",
    ""com.unity.visualscripting"": ""1.9.4"",
    ""com.unity.inputsystem"": ""1.7.0""
  },
  ""scopedRegistries"": [
    {
      ""name"": ""OpenUPM"",
      ""url"": ""https://package.openupm.com"",
      ""scopes"": [
        ""com.google.xr.cardboard"",
        ""com.openupm""
      ]
    }
  ]
}";

            File.WriteAllText(manifestPath, fixedManifest);
            Debug.Log("[MetaPort] Manifest corrigido! Agora vai em Package Manager e adiciona com.google.xr.cardboard ou ignora e usa fallback");

            // Mostra janela
            EditorApplication.delayCall += () =>
            {
                if (EditorUtility.DisplayDialog("MetaPort - Manifest Corrigido!", 
                    "Corrigi seu Packages/manifest.json automaticamente!\n\nAgora você tem 2 opções:\n\n1. PREGUIÇOSO (Recomendado): Clica em Continue e usa sem Cardboard - funciona 100% com ARCore + Gyro\n\n2. Completo: Vai em Window -> Package Manager -> + Add by name -> com.google.xr.cardboard\n\nO app funciona dos dois jeitos!", 
                    "Sou preguiçoso, usar sem Cardboard", "Quero instalar Cardboard"))
                {
                    Debug.Log("[MetaPort] Modo preguiçoso ativado - usando fallback ARCore");
                }
            };
        }

        [MenuItem("MetaPort/FIX - Corrigir Manifest (1 clique)")]
        public static void FixManifestManual()
        {
            string manifestPath = Path.Combine(Application.dataPath, "../Packages/manifest.json");
            string fixedManifest = @"{
  ""dependencies"": {
    ""com.unity.xr.arfoundation"": ""5.1.2"",
    ""com.unity.xr.arcore"": ""5.1.2"",
    ""com.unity.xr.openxr"": ""1.9.1"",
    ""com.unity.xr.management"": ""4.4.0"",
    ""com.unity.xr.legacyinputhelpers"": ""2.1.10"",
    ""com.unity.render-pipelines.universal"": ""14.0.11"",
    ""com.unity.textmeshpro"": ""3.0.6"",
    ""com.unity.timeline"": ""1.7.6"",
    ""com.unity.visualscripting"": ""1.9.4"",
    ""com.unity.inputsystem"": ""1.7.0""
  },
  ""scopedRegistries"": [
    {
      ""name"": ""OpenUPM"",
      ""url"": ""https://package.openupm.com"",
      ""scopes"": [
        ""com.google.xr.cardboard"",
        ""com.openupm""
      ]
    }
  ]
}";
            File.WriteAllText(manifestPath, fixedManifest);
            Debug.Log("[MetaPort] Manifest corrigido manualmente!");
            AssetDatabase.Refresh();
            EditorUtility.DisplayDialog("MetaPort", "Manifest corrigido! Pode dar Retry agora.", "OK");
        }
    }
}
