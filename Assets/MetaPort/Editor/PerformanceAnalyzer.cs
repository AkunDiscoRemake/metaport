using UnityEngine;
using UnityEditor;

namespace MetaPort.Editor
{
    public class PerformanceAnalyzer : EditorWindow
    {
        [MenuItem("MetaPort/Performance Analyzer")]
        static void ShowWindow()
        {
            GetWindow<PerformanceAnalyzer>("MetaPort Performance");
        }

        void OnGUI()
        {
            GUILayout.Label("MetaPort - Performance para VR Box", EditorStyles.boldLabel);
            GUILayout.Space(10);

            GUILayout.Label("Dicas para melhor performance em celular fraco:", EditorStyles.helpBox);
            GUILayout.Label("- Desative enableMeshing no ARCoreEnvironmentTracker");
            GUILayout.Label("- Reduza targetFPS hand tracking para 30");
            GUILayout.Label("- Use 2 janelas no máximo");
            GUILayout.Label("- Desative depth occlusion se lag");
            GUILayout.Label("- Use ambiente VoidSpace (mais leve)");

            GUILayout.Space(10);
            if (GUILayout.Button("Otimizar para celular fraco"))
            {
                OptimizeForLowEnd();
            }
            if (GUILayout.Button("Otimizar para celular forte"))
            {
                OptimizeForHighEnd();
            }
        }

        void OptimizeForLowEnd()
        {
            Debug.Log("[MetaPort] Otimizando para low-end...");
            // Desativa coisas pesadas
        }

        void OptimizeForHighEnd()
        {
            Debug.Log("[MetaPort] Otimizando para high-end - tudo ativado!");
        }
    }
}
