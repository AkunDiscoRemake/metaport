using UnityEngine;
using UnityEditor;
using System.IO;

namespace MetaPort.Editor
{
    /// <summary>
    /// MetaPort Builder - Builds Android APK and UnityPackage
    /// </summary>
    public class MetaPortBuilder : MonoBehaviour
    {
        [MenuItem("MetaPort/Build Android APK (Cardboard VR)")]
        public static void BuildAndroidAPK()
        {
            Debug.Log("[MetaPort] Building Android APK...");

            string buildPath = Path.Combine(Application.dataPath, "../Builds/MetaPort_CardboardVR.apk");
            Directory.CreateDirectory(Path.GetDirectoryName(buildPath));

            // Set build settings
            PlayerSettings.productName = "MetaPort";
            PlayerSettings.companyName = "MetaPort";
            PlayerSettings.bundleVersion = "1.0.0";
            PlayerSettings.Android.bundleVersionCode = 1;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel24;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
            PlayerSettings.Android.preferredInstallLocation = AndroidPreferredInstallLocation.Auto;
            PlayerSettings.Android.forceSDCardPermission = true;
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.gpuSkinning = true;
            PlayerSettings.stereoRenderingPath = StereoRenderingPath.SinglePassInstanced;
            PlayerSettings.virtualRealitySupported = true;
            PlayerSettings.virtualRealitySDKs = new string[] { "cardboard", "OpenXR" };

            // XR Settings
            PlayerSettings.Android.ARCoreEnabled = true;

            // Scenes
            string[] scenes = { "Assets/MetaPort/Scenes/Main.unity" };

            BuildPipeline.BuildPlayer(scenes, buildPath, BuildTarget.Android, BuildOptions.None);
            Debug.Log($"[MetaPort] APK built at {buildPath}");
        }

        [MenuItem("MetaPort/Build UnityPackage (SDK + Games)")]
        public static void BuildUnityPackage()
        {
            Debug.Log("[MetaPort] Building UnityPackage...");

            string[] assets = {
                "Assets/MetaPort/Core",
                "Assets/MetaPort/HandTracking",
                "Assets/MetaPort/UI",
                "Assets/MetaPort/Environments",
                "Assets/MetaPort/Games",
                "Assets/MetaPort/SDK",
                "Assets/MetaPort/Shaders",
                "Assets/MetaPort/Android",
                "Assets/MetaPort/Prefabs",
                "Assets/MetaPort/Materials"
            };

            string packagePath = Path.Combine(Application.dataPath, "../../MetaPort_CardboardVR_SDK_v1.0.unitypackage");
            AssetDatabase.ExportPackage(assets, packagePath, ExportPackageOptions.Recurse | ExportPackageOptions.IncludeDependencies);
            Debug.Log($"[MetaPort] UnityPackage built at {packagePath}");
        }

        [MenuItem("MetaPort/Setup Project (Cardboard + ARCore + Monado)")]
        public static void SetupProject()
        {
            Debug.Log("[MetaPort] Setting up project...");

            // Install required packages via Package Manager
            // com.google.xr.cardboard
            // com.unity.xr.arcore
            // com.unity.xr.arfoundation
            // com.unity.xr.openxr
            // com.unity.xr.management
            // com.google.mediapipe.tasks.vision

            Debug.Log("[MetaPort] Please install via Package Manager:");
            Debug.Log("- com.google.xr.cardboard");
            Debug.Log("- com.unity.xr.arfoundation 5.1.0");
            Debug.Log("- com.unity.xr.arcore 5.1.0");
            Debug.Log("- com.unity.xr.openxr 1.8.2");
            Debug.Log("- com.unity.xr.management 4.3.3");
            Debug.Log("- URP 14.0.8");

            // Create main scene
            var scene = UnityEditor.SceneManagement.EditorSceneManager.NewScene(UnityEditor.SceneManagement.NewSceneSetup.DefaultGameObjects);
            
            // Add rig
            var rigGO = new GameObject("MetaPort_Rig");
            rigGO.AddComponent<Core.Cardboard.CardboardRig>();
            rigGO.AddComponent<Core.ARCore6DoFManager>();
            rigGO.AddComponent<Core.MonadoOpenXRLoader>();
            rigGO.AddComponent<Core.MixedReality.MRModeManager>();
            rigGO.AddComponent<HandTracking.HandTrackingManager>();
            rigGO.AddComponent<Environments.EnvironmentManager>();
            rigGO.AddComponent<Games.GameManager>();
            rigGO.AddComponent<UI.SpatialUI.DockManager>();
            rigGO.AddComponent<UI.SpatialUI.AppLibrary>();

            UnityEditor.SceneManagement.EditorSceneManager.SaveScene(scene, "Assets/MetaPort/Scenes/Main.unity");
            Debug.Log("[MetaPort] Project setup complete!");
        }
    }
}
