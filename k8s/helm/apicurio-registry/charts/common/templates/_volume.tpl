{{- /*
  common.volume.awsElasticBlockStore
  common.volume.azureDisk
  common.volume.azureFile
  common.volume.cephfs
  common.volume.cinder
  - common.volume.configMap
  common.volume.csi
  common.volume.downwardAPI
  - common.volume.emptyDir
  common.volume.fc
  common.volume.flexVolume
  common.volume.flocker
  common.volume.gcePersistentDisk
  common.volume.gitRepo
  common.volume.glusterfs
  - common.volume.hostPath
  common.volume.iscsi
  common.volume.nfs
  - common.volume.persistentVolumeClaim
  common.volume.photonPersistentDisk
  common.volume.portworxVolume
  common.volume.projected
  common.volume.quobyte
  common.volume.rbd
  common.volume.scaleIO
  - common.volume.secret
  common.volume.storageos
  common.volume.vsphereVolume
*/ -}}

{{- define "common.volume.configMap" -}}
        {{- $name := index . 0 -}}
        {{- $configMapName := index . 1 -}}
        {{- $defaultMode := index . 2 -}}

        name: {{ $name }}
        configMap:
          defaultMode: {{ $defaultMode | default 0644 }}
          name: {{ $configMapName }}
{{- end -}}

{{- define "common.volume.cronjob.configMap" -}}
            {{- $name := index . 0 -}}
            {{- $configMapName := index . 1 -}}
            {{- $defaultMode := index . 2 -}}

            name: {{ $name }}
            configMap:
              defaultMode: {{ $defaultMode | default 0644 }}
              name: {{ $configMapName }}
{{- end -}}

{{- define "common.pod.volume.configMap" -}}
    {{- $name := index . 0 -}}
    {{- $configMapName := index . 1 -}}
    {{- $defaultMode := index . 2 -}}

    name: {{ $name }}
    configMap:
      defaultMode: {{ $defaultMode | default 0644 }}
      name: {{ $configMapName }}
{{- end -}}

{{- define "common.volume.host" -}}
        {{- $name := index . 0 -}}
        {{- $path := index . 1 -}}

        name: {{ $name }}
        hostPath:
          path: {{ $path | default "/sys" }}
{{- end -}}

{{- define "common.volume.empty" -}}
        {{- $name := index . 0 -}}

        name: {{ $name }}
        emptyDir: {}
{{- end -}}


{{- /*
  common.volume.pvc
  kind: PersistentVolumeClaim
*/ -}}
{{- define "common.volume.pvc" -}}
        {{- $name := index . 0 -}}
        {{- $claimName := index . 1 -}}
        {{- $persistence := index . 2 -}}

        name: {{ $name }}
        {{- if $persistence.enabled }}
        persistentVolumeClaim:
          claimName: {{ $persistence.existingClaim | default $claimName }}
        {{- else }}
        emptyDir: {}
        {{- end -}}
{{- end -}}

{{- /*
  common.volume.cj.pvc
  kind: PersistentVolumeClaim
*/ -}}
{{- define "common.volume.cj.pvc" -}}
            {{- $name := index . 0 -}}
            {{- $claimName := index . 1 -}}
            {{- $persistence := index . 2 -}}

            name: {{ $name }}
            {{- if $persistence.enabled }}
            persistentVolumeClaim:
              claimName: {{ $persistence.existingClaim | default $claimName }}
            {{- else }}
            emptyDir: {}
            {{- end -}}
{{- end -}}

{{- /*
  common.volume.secret
  kind:
*/ -}}
{{- define "common.volume.secret" -}}
    {{- $name := index . 0 -}}
    {{- $secretName := index . 1 -}}

        name: {{ $name }}
        secret:
          secretName: {{ $secretName }}
{{- end -}}
