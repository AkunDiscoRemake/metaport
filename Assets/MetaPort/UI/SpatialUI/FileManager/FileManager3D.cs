using UnityEngine;
using System.IO;
using System.Collections.Generic;

namespace MetaPort.UI.SpatialUI.FileManager
{
    /// <summary>
    /// File Manager 3D espacial - navega arquivos em 3D
    /// </summary>
    public class FileManager3D : MonoBehaviour
    {
        [Header("File Manager 3D")]
        public string currentPath = "/sdcard";
        public Transform filesRoot;
        public float iconSize = 0.18f;
        public float spacing = 0.05f;

        private List<FileItem3D> _items = new List<FileItem3D>();

        void Awake()
        {
            if (filesRoot == null) filesRoot = transform;
            Refresh();
        }

        public void Refresh()
        {
            Clear();

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var dirs = Directory.GetDirectories(currentPath);
                var files = Directory.GetFiles(currentPath);

                int i = 0;
                foreach (var dir in dirs)
                {
                    CreateItem(Path.GetFileName(dir), true, i++);
                    if (i > 20) break;
                }
                foreach (var file in files)
                {
                    CreateItem(Path.GetFileName(file), false, i++);
                    if (i > 20) break;
                }
            }
            catch
            {
                CreateItem("No access", false, 0);
            }
#else
            // Mock no editor
            for (int i = 0; i < 12; i++)
                CreateItem(i % 3 == 0 ? $"Folder_{i}" : $"File_{i}.mp4", i % 3 == 0, i);
#endif
        }

        void CreateItem(string name, bool isFolder, int index)
        {
            int cols = 4;
            int col = index % cols;
            int row = index / cols;
            Vector3 pos = new Vector3(
                (col - cols * 0.5f + 0.5f) * (iconSize + spacing),
                0.5f - row * (iconSize + spacing),
                0.05f
            );

            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = $"File_{name}_3D";
            go.transform.SetParent(filesRoot, false);
            go.transform.localPosition = pos;
            go.transform.localScale = new Vector3(iconSize, iconSize, 0.02f);

            var mat = new Material(Shader.Find("MetaPort/Icon3D"));
            mat.SetColor("_BaseColor", isFolder ? new Color(0.9f, 0.8f, 0.3f) : new Color(0.5f, 0.6f, 0.9f));
            go.GetComponent<Renderer>().material = mat;

            var item = go.AddComponent<FileItem3D>();
            item.fileName = name;
            item.isFolder = isFolder;
            item.manager = this;
            _items.Add(item);
        }

        void Clear()
        {
            foreach (var item in _items) if (item != null) Destroy(item.gameObject);
            _items.Clear();
        }

        public void OpenItem(FileItem3D item)
        {
            if (item.isFolder)
            {
                currentPath = Path.Combine(currentPath, item.fileName);
                Refresh();
            }
            else
            {
                Debug.Log($"[FileManager] Opening {item.fileName}");
                // Abriria com app adequado - vídeo em janela 3D etc
                var window = SDK.MetaPortAPI.CreateSpatialWindow(item.fileName, new Vector3(1.2f, 0.8f, 0.05f));
            }
        }
    }

    public class FileItem3D : MonoBehaviour, IHandInteractable
    {
        public string fileName;
        public bool isFolder;
        public FileManager3D manager;

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand) => transform.localScale = Vector3.one * 0.2f;
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => manager.OpenItem(this);
        public void OnHandRelease() => transform.localScale = new Vector3(0.18f, 0.18f, 0.02f);
        public void OnTriggerEnter(Vector3 point) => manager.OpenItem(this);
    }
}
