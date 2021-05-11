{{- define "common.volumemounts" -}}
        {{- $name := index . 0 -}}
        {{- $mountPath := index . 1 -}}
        {{- $mountPropagation := index . 2 -}}
        {{- $readOnly := index . 3 -}}

          name: {{ $name }}
          mountPath: {{ $mountPath }}
          mountPropagation: {{ $mountPropagation | default "HostToContainer" }}
          readOnly: {{ $readOnly | default true }}
{{- end -}}

{{- define "common.volumemounts.pv" -}}
        {{- $name := index . 0 -}}
        {{- $mountPath := index . 1 -}}

          name: {{ $name }}
          mountPath: {{ $mountPath }}
{{- end -}}
