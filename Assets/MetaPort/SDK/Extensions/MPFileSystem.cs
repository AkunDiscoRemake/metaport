using UnityEngine;
using System.IO;

namespace MetaPort.SDK.Extensions
{
    /// <summary>
    /// File system API para devs
    /// </summary>
    public static class MPFileSystem
    {
        public static string GetAppDataPath(string appId)
        {
            string path = Path.Combine(Application.persistentDataPath, appId);
            Directory.CreateDirectory(path);
            return path;
        }

        public static bool SaveFile(string appId, string fileName, byte[] data)
        {
            try
            {
                string path = Path.Combine(GetAppDataPath(appId), fileName);
                File.WriteAllBytes(path, data);
                return true;
            }
            catch { return false; }
        }

        public static byte[] LoadFile(string appId, string fileName)
        {
            try
            {
                string path = Path.Combine(GetAppDataPath(appId), fileName);
                return File.ReadAllBytes(path);
            }
            catch { return null; }
        }
    }

    public static class MPNetwork
    {
        public static bool IsConnected => Application.internetReachability != NetworkReachability.NotReachable;

        public static void DownloadFile(string url, System.Action<byte[]> onComplete)
        {
            // Usaria UnityWebRequest
            Debug.Log($"[MPNetwork] Downloading {url}");
        }
    }

    public static class MPHaptics
    {
        public static void Vibrate(float duration = 0.1f)
        {
            Handheld.Vibrate();
        }

        public static void VibrateHand(Core.MonadoOpenXRLoader.Handedness hand, float amplitude = 1f)
        {
            // Em Monado, usaria OpenXR haptics
            // Em Cardboard, vibração do celular
            Handheld.Vibrate();
        }
    }
}
