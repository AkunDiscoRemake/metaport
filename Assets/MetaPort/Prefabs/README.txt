Prefabs MetaPort - Todos 100% 3D SPATIAL

Para usar, arraste para cena:

- CardboardRig.prefab - Rig completo com 6DOF + ARCore + Monado
- SpatialWindow.prefab - Janela curva 3D
- Dock.prefab - Dock inferior 3D
- AppLibrary.prefab - Biblioteca de apps grid 3D
- HandTrackingRig.prefab - Visualização das mãos
- Environment_TropicalIsland.prefab
- Environment_JapaneseDojo.prefab
- Game_BeatSaber.prefab
- Game_PongSpatial.prefab
- etc

Todos os prefabs usam MeshRenderer 3D com profundidade, não Canvas Screen Space.

Para criar prefab novo:
1. Crie GameObject com MeshFilter + MeshRenderer
2. Use shader MetaPort/SpatialWindow
3. Adicione BoxCollider para interação 3D
4. Adicione componente IHandInteractable
