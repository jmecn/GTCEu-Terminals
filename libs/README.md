# 📚 Carpeta de Librerías

## ¿Qué va aquí?

Necesitas **DOS archivos JAR** para compilar el mod:

1. **GregTech Modern 7.4.0**: `gtceu-1.20.1-7.4.0.jar`
2. **LDLib 1.0.31**: `ldlib-forge-1.20.1-1.0.31.jar`

## 📥 Cómo Obtener los JARs

### 1. GregTech Modern 7.4.0

**Modrinth:**
- URL: https://modrinth.com/mod/gregtechceu-modern/version/mc1.20.1-7.4.0-forge
- Archivo: `gtceu-1.20.1-7.4.0.jar` (~17 MB)

**CurseForge:**
- URL: https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern/files
- Busca versión 7.4.0 para 1.20.1 Forge

### 2. LDLib 1.0.31

**Modrinth:**
- URL: https://modrinth.com/mod/ldlib/version/1.0.31+forge
- Archivo: `ldlib-forge-1.20.1-1.0.31.jar` (~2 MB)

**CurseForge:**
- URL: https://www.curseforge.com/minecraft/mc-mods/ldlib/files
- Busca versión 1.0.31 para 1.20.1 Forge

## 📁 Estructura Final

Después de copiar AMBOS JARs, debería verse así:

```
libs/
├── README.md                           ← Este archivo
├── gtceu-1.20.1-7.4.0.jar             ← GregTech Modern
└── ldlib-forge-1.20.1-1.0.31.jar      ← LDLib
```

## ✅ Verificar

Para verificar que ambos archivos están correctamente colocados:

**Windows PowerShell:**
```powershell
ls libs/*.jar
```

**CMD:**
```batch
dir libs\*.jar
```

Deberías ver AMBOS archivos:
```
gtceu-1.20.1-7.4.0.jar
ldlib-forge-1.20.1-1.0.31.jar
```

## ⚙️ build.gradle

El `build.gradle` ya está configurado para usar ambos JARs:

```gradle
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    
    implementation files('libs/gtceu-1.20.1-7.4.0.jar')
    implementation files('libs/ldlib-forge-1.20.1-1.0.31.jar')
}
```

**No necesitas modificar nada** - solo pon los JARs aquí.

## 🚀 Compilar

Una vez ambos JARs estén aquí:

```powershell
.\compilar.ps1
```

O:
```cmd
compilar-simple.bat
```

## ⚠️ Nota Importante

**NO** subas estos JARs a Git/GitHub. Son archivos grandes y tienen sus propias licencias.

El `.gitignore` ya está configurado para ignorarlos:
```
libs/*.jar
```

Otros desarrolladores deberán descargar sus propias copias.

## 💡 ¿Por Qué Necesitas Ambos?

- **GregTech Modern**: El mod principal que estamos extendiendo
- **LDLib**: Librería requerida por GregTech Modern 7.4.0+

Sin LDLib, obtendrás errores de compilación como:
```
error: cannot access IEnhancedManaged
```

## 🆘 Problemas

Si la compilación falla:

1. ✅ Verifica que AMBOS archivos existen
2. ✅ Verifica que los nombres son EXACTOS
3. ✅ Lee `NECESITAS_LDLIB.md` para más ayuda

---

**¿Listo?** Una vez tengas ambos JARs, ejecuta `.\compilar.ps1` 🚀
