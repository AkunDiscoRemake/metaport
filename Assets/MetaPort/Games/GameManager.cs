using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.Games
{
    /// <summary>
    /// Game Manager - launches spatial 3D games
    /// </summary>
    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance;

        [Header("Games - Quest Remakes")]
        public List<GameDefinition> games = new List<GameDefinition>();

        [Header("Current")]
        public string currentGame = "";
        public Transform gameRoot;

        void Awake()
        {
            Instance = this;
            if (gameRoot == null) gameRoot = transform;
            PopulateGames();
        }

        void PopulateGames()
        {
            if (games.Count > 0) return;
            games.Add(new GameDefinition{ id="BeatSaberRemake", name="Beat Saber", description="Slash the beats in 3D spatial", prefabName="BeatSaberRemake" });
            games.Add(new GameDefinition{ id="PongSpatial", name="Pong Spatial", description="Classic pong in 3D MR", prefabName="PongSpatial" });
            games.Add(new GameDefinition{ id="FishingVR", name="Real VR Fishing", description="Relaxing fishing in tropical island", prefabName="FishingVR" });
            games.Add(new GameDefinition{ id="MiniGolf", name="Walkabout Mini Golf", description="Mini golf with friends", prefabName="MiniGolf" });
            games.Add(new GameDefinition{ id="SmashDrums", name="Smash Drums", description="Drum in spatial", prefabName="SmashDrums" });
            games.Add(new GameDefinition{ id="PuzzlingPlaces", name="Puzzling Places", description="3D puzzles from real world scans", prefabName="PuzzlingPlaces" });
            games.Add(new GameDefinition{ id="FirstSteps", name="First Steps", description="Hand tracking introduction", prefabName="FirstSteps" });
            games.Add(new GameDefinition{ id="Tetris3D", name="Tetris Effect", description="Tetris in volumetric 3D", prefabName="Tetris3D" });
            games.Add(new GameDefinition{ id="SuperhotVR", name="Superhot VR", description="Time moves when you move - 6DOF", prefabName="SuperhotVR" });
            games.Add(new GameDefinition{ id="AsgardWrathMini", name="Asgard's Wrath", description="Sword and shield combat", prefabName="AsgardWrathMini" });
            games.Add(new GameDefinition{ id="GorillaTagMini", name="Gorilla Tag", description="Tag with gorilla locomotion", prefabName="GorillaTagMini" });
            games.Add(new GameDefinition{ id="JobSimulator", name="Job Simulator", description="Fun job tasks in 3D", prefabName="JobSimulator" });
        }

        public void LaunchGame(string gameId)
        {
            // Clear current
            foreach (Transform child in gameRoot) Destroy(child.gameObject);

            var def = games.Find(g => g.id == gameId || g.name == gameId);
            if (def == null) def = games[0];

            currentGame = def.id;
            Debug.Log($"[MetaPort] Launching game: {def.name}");

            GameObject gameObj = new GameObject($"Game_{def.id}");
            gameObj.transform.SetParent(gameRoot, false);
            gameObj.transform.localPosition = Vector3.forward * 2f;

            // Add specific game component
            switch(def.id)
            {
                case "BeatSaberRemake": gameObj.AddComponent<BeatSaberRemake.BeatSaberManager>(); break;
                case "PongSpatial": gameObj.AddComponent<PongSpatial.Pong3D>(); break;
                case "FishingVR": gameObj.AddComponent<FishingVR.FishingManager>(); break;
                case "MiniGolf": gameObj.AddComponent<MiniGolf.GolfManager>(); break;
                case "SmashDrums": gameObj.AddComponent<SmashDrums.SmashDrumsManager>(); break;
                case "PuzzlingPlaces": gameObj.AddComponent<PuzzlingPlaces.PuzzlingManager>(); break;
                case "FirstSteps": gameObj.AddComponent<FirstSteps.FirstStepsManager>(); break;
                case "Tetris3D": gameObj.AddComponent<Tetris3D.Tetris3DManager>(); break;
                case "SuperhotVR": gameObj.AddComponent<SuperhotVR.SuperhotManager>(); break;
                case "AsgardWrathMini": gameObj.AddComponent<AsgardWrathMini.AsgardMini>(); break;
                case "GorillaTagMini": gameObj.AddComponent<GorillaTagMini.GorillaTagMini>(); break;
                case "JobSimulator": gameObj.AddComponent<JobSimulator.JobSimMini>(); break;
            }
        }

        public void ExitGame()
        {
            foreach (Transform child in gameRoot) Destroy(child.gameObject);
            currentGame = "";
        }
    }

    [System.Serializable]
    public class GameDefinition
    {
        public string id;
        public string name;
        public string description;
        public string prefabName;
        public Sprite icon;
    }
}
