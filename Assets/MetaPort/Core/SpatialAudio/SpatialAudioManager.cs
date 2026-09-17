using UnityEngine;
using System.Collections.Generic;

namespace MetaPort.Core.SpatialAudio
{
    /// <summary>
    /// Áudio espacial 3D - essencial para melhor app VR Box
    /// Usa Resonance Audio / Steam Audio style
    /// </summary>
    public class SpatialAudioManager : MonoBehaviour
    {
        public static SpatialAudioManager Instance;

        [Header("Spatial Audio")]
        public bool enableSpatialAudio = true;
        public bool enableOcclusion = true;
        public bool enableReverb = true;
        public float globalVolume = 1f;

        private List<AudioSource> _spatialSources = new List<AudioSource>();

        void Awake()
        {
            Instance = this;
            // Configura audio para HRTF
            AudioSettings.speakerMode = AudioSpeakerMode.Stereo;
        }

        public AudioSource PlaySpatialSound(AudioClip clip, Vector3 position, float volume = 1f, bool loop = false)
        {
            var go = new GameObject($"SpatialSound_{clip.name}");
            go.transform.position = position;
            var source = go.AddComponent<AudioSource>();
            source.clip = clip;
            source.spatialBlend = 1f; // 3D
            source.rolloffMode = AudioRolloffMode.Logarithmic;
            source.minDistance = 0.5f;
            source.maxDistance = 20f;
            source.volume = volume * globalVolume;
            source.loop = loop;
            source.Play();

            _spatialSources.Add(source);

            if (!loop) Destroy(go, clip.length + 0.1f);

            return source;
        }

        public AudioSource PlayUISound(AudioClip clip, float volume = 1f)
        {
            // UI sound - 2D but with slight spatial
            var go = new GameObject($"UISound_{clip.name}");
            go.transform.SetParent(transform, false);
            var source = go.AddComponent<AudioSource>();
            source.clip = clip;
            source.spatialBlend = 0.1f;
            source.volume = volume * globalVolume;
            source.Play();
            Destroy(go, clip.length + 0.1f);
            return source;
        }

        void Update()
        {
            if (!enableOcclusion) return;
            // Raycast para oclusão - se parede entre ouvinte e fonte, abaixa volume
            foreach (var source in _spatialSources)
            {
                if (source == null) continue;
                Vector3 listenerPos = Camera.main.transform.position;
                Vector3 dir = source.transform.position - listenerPos;
                if (Physics.Raycast(listenerPos, dir.normalized, out var hit, dir.magnitude))
                {
                    // Ocluído
                    source.volume = Mathf.Lerp(source.volume, globalVolume * 0.3f, Time.deltaTime * 5f);
                }
                else
                {
                    source.volume = Mathf.Lerp(source.volume, globalVolume, Time.deltaTime * 5f);
                }
            }
        }
    }
}
