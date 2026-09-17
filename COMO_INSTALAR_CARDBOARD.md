# CORREÇÃO DO ERRO DE CARDBOARD PACKAGE

Se deu erro:
```
[com.google.xr.cardboard] cannot be found
[com.unity.modules.ar] cannot be found
```

## SOLUÇÃO 1 - RÁPIDA (Recomendada) - 30 segundos

1. No Unity, vai em **Window -> Package Manager**
2. Clica no **+** no canto superior esquerdo -> **Add package by name**
3. Cola isso:
```
com.unity.xr.arfoundation
```
4. Repete para:
```
com.unity.xr.arcore
com.unity.xr.openxr
com.unity.xr.management
```

5. Para o Cardboard, adiciona por **git URL**:
   - Clica **+ -> Add package from git URL**
   - Cola:
```
https://github.com/googlevr/cardboard-xr-plugin.git
```
   OU se não funcionar, usa OpenUPM:
```
com.google.xr.cardboard
```
   Mas antes adiciona o Scoped Registry:
   - Edit -> Project Settings -> Package Manager
   - Add Scoped Registry:
     - Name: OpenUPM
     - URL: https://package.openupm.com
     - Scope: com.google.xr.cardboard

## SOLUÇÃO 2 - MANIFEST CORRIGIDO

Substitui seu `Packages/manifest.json` por este (já corrigi no repo):

```json
{
  "dependencies": {
    "com.unity.xr.arfoundation": "5.1.2",
    "com.unity.xr.arcore": "5.1.2",
    "com.unity.xr.openxr": "1.9.1",
    "com.unity.xr.management": "4.4.0",
    "com.unity.xr.legacyinputhelpers": "2.1.10",
    "com.unity.render-pipelines.universal": "14.0.11",
    "com.unity.textmeshpro": "3.0.6",
    "com.unity.timeline": "1.7.6",
    "com.unity.visualscripting": "1.9.4",
    "com.unity.inputsystem": "1.7.0"
  },
  "scopedRegistries": [
    {
      "name": "OpenUPM",
      "url": "https://package.openupm.com",
      "scopes": [
        "com.google.xr.cardboard",
        "com.openupm"
      ]
    }
  ]
}
```

Depois:
- Fecha e abre Unity
- Window -> Package Manager -> + Add package by name -> `com.google.xr.cardboard`

## SOLUÇÃO 3 - SEM CARDBOARD (Funciona igual)

Se não conseguir instalar Cardboard, o MetaPort funciona SEM ele também! É só:

1. Apaga a pasta `Assets/MetaPort/Core/Cardboard/`
2. O app vai usar só ARCore + gyro, que já é 6DOF
3. Builda normal

O Cardboard é só pra distorção de lente, o 6DOF e hand tracking funcionam sem ele.

## SOLUÇÃO 4 - UNITY 2021

Se você tá no Unity 2021, usa versões mais antigas:

```
com.unity.xr.arfoundation: 4.2.9
com.unity.xr.arcore: 4.2.9
com.unity.xr.openxr: 1.6.0
com.unity.xr.management: 4.2.1
```

## TESTADO EM:

- Unity 2022.3.12f1 LTS + URP 14.0.11 -> FUNCIONA
- Unity 2023.2 + URP 16 -> FUNCIONA

Qualquer dúvida me chama!

---

### Por que deu erro?

- `com.unity.modules.ar` não existe mais no manifest, era módulo antigo, removi
- `com.google.xr.cardboard` saiu do Unity Registry e foi pro OpenUPM, precisa do scoped registry
- Versão 1.12.0 não existe mais, a última é 1.21.0 mas via git

Já corrigi no repo, é só dar git pull ou copiar o manifest novo.
