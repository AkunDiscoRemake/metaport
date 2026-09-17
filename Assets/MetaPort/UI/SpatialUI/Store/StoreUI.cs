using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.UI.SpatialUI.Store
{
    /// <summary>
    /// Store 3D - loja de apps e jogos estilo Quest Store
    /// </summary>
    public class StoreUI : MonoBehaviour
    {
        [Header("Store 3D")]
        public Transform appsRoot;
        public List<StoreApp> storeApps = new List<StoreApp>();

        void Awake()
        {
            if (appsRoot == null) appsRoot = transform;
            PopulateStore();
        }

        void PopulateStore()
        {
            storeApps.Add(new StoreApp{ id="beatsaber", name="Beat Saber", price="R$ 89,90", category="Games", rating=4.9f });
            storeApps.Add(new StoreApp{ id="fishing", name="Real VR Fishing", price="R$ 49,90", category="Games", rating=4.8f });
            storeApps.Add(new StoreApp{ id="minigolf", name="Walkabout Mini Golf", price="R$ 59,90", category="Games", rating=4.9f });
            storeApps.Add(new StoreApp{ id="superhot", name="Superhot VR", price="R$ 69,90", category="Games", rating=4.7f });
            storeApps.Add(new StoreApp{ id="asgard", name="Asgard's Wrath 2", price="R$ 149,90", category="Games", rating=4.8f });
            storeApps.Add(new StoreApp{ id="gorillatag", name="Gorilla Tag", price="Grátis", category="Games", rating=4.6f });
            storeApps.Add(new StoreApp{ id="jobsim", name="Job Simulator", price="R$ 39,90", category="Games", rating=4.5f });

            int cols = 3;
            float cellW = 0.4f, cellH = 0.5f, spacing = 0.05f;

            for (int i = 0; i < storeApps.Count; i++)
            {
                int col = i % cols;
                int row = i / cols;
                Vector3 pos = new Vector3(
                    (col - cols * 0.5f + 0.5f) * (cellW + spacing),
                    0.5f - row * (cellH + spacing),
                    0.05f
                );

                var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = $"Store_{storeApps[i].name}_3D";
                go.transform.SetParent(appsRoot, false);
                go.transform.localPosition = pos;
                go.transform.localScale = new Vector3(cellW, cellH, 0.04f);

                var mat = new Material(Shader.Find("MetaPort/SpatialWindow"));
                mat.SetColor("_BaseColor", new Color(0.15f,0.18f,0.22f,0.95f));
                go.GetComponent<Renderer>().material = mat;

                var item = go.AddComponent<StoreItem3D>();
                item.app = storeApps[i];
                item.store = this;
            }
        }

        public void BuyApp(StoreApp app)
        {
            Debug.Log($"[Store] Buying {app.name} for {app.price}");
            // Integraria com Play Billing ou download
            var gameManager = Games.GameManager.Instance;
            if (gameManager != null) gameManager.LaunchGame(app.id);
        }
    }

    [System.Serializable]
    public class StoreApp
    {
        public string id;
        public string name;
        public string price;
        public string category;
        public float rating;
        public Sprite cover;
    }

    public class StoreItem3D : MonoBehaviour, IHandInteractable
    {
        public StoreApp app;
        public StoreUI store;

        public void OnHandHover(Vector3 point, Core.MonadoOpenXRLoader.Handedness hand) => transform.localScale *= 1.05f;
        public void OnHandGrab(Core.MonadoOpenXRLoader.Handedness hand, Vector3 grabPoint, Quaternion grabRot) => store.BuyApp(app);
        public void OnHandRelease() {}
        public void OnTriggerEnter(Vector3 point) => store.BuyApp(app);
    }
}
