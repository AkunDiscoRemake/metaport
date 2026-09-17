using UnityEngine;
using UnityEditor;

namespace MetaPort.Editor
{
    /// <summary>
    /// Para preguiçosos - 1 clique pra tudo funcionar
    /// </summary>
    public class NoSetupRequired : MonoBehaviour
    {
        [MenuItem("MetaPort/PREGUIÇOSO - 1 Clique para Funcionar")]
        public static void OneClickSetup()
        {
            Debug.Log("[MetaPort] Modo preguiçoso ativado!");

            // 1. Corrige manifest
            AutoFixManifest.FixManifestManual();

            // 2. Cria cena se não existe
            if (!System.IO.File.Exists("Assets/MetaPort/Scenes/Main.unity"))
            {
                System.IO.Directory.CreateDirectory("Assets/MetaPort/Scenes");
                var scene = UnityEditor.SceneManagement.EditorSceneManager.NewScene(UnityEditor.SceneManagement.NewSceneSetup.DefaultGameObjects);
                
                var rigGO = new GameObject("MetaPort_Rig_PREGUICOSO");
                rigGO.AddComponent<Core.Cardboard.CardboardRig>();
                rigGO.AddComponent<Core.Cardboard.CardboardFallback>();
                rigGO.AddComponent<Core.ARCore.ARCore6DoFManager>();
                rigGO.AddComponent<Core.MonadoOpenXRLoader>();
                rigGO.AddComponent<Core.MixedReality.MRModeManager>();
                rigGO.AddComponent<Core.Calibration.CardboardCalibrator>();
                rigGO.AddComponent<Core.Guardian.GuardianSystem>();
                rigGO.AddComponent<Core.Comfort.ComfortManager>();
                rigGO.AddComponent<Core.SpatialAudio.SpatialAudioManager>();
                rigGO.AddComponent<HandTracking.HandTrackingManager>();
                rigGO.AddComponent<Environments.EnvironmentManager>();
                rigGO.AddComponent<Games.GameManager>();
                rigGO.AddComponent<UI.SpatialUI.DockManager>();
                rigGO.AddComponent<UI.SpatialUI.AppLibrary>();
                rigGO.AddComponent<UI.SpatialUI.WindowManager>();

                UnityEditor.SceneManagement.EditorSceneManager.SaveScene(scene, "Assets/MetaPort/Scenes/Main.unity");
                Debug.Log("[MetaPort] Cena Main criada!");
            }

            EditorUtility.DisplayDialog("MetaPort - Pronto!", 
                "Feito! Agora é só apertar PLAY!\n\n" +
                "- Segura H para testar mão\n" +
                "- Mouse para gaze\n" +
                "- M para trocar VR/MR\n\n" +
                "Tudo funciona sem precisar instalar Cardboard!", 
                "Vou jogar!");
        }
    }
}
