{{- /*
*/ -}}

{{- define "common.envvar.key" -}}
  {{- range $key, $value := .Values.env }}
  name: {{ $key | upper | replace "." "_" }}
  value: {{ default "" $value | quote }}
  {{- end }}
{{- end -}}

{{- define "common.envvar.value" -}}
  {{- $name := index . 0 -}}
  {{- $value := index . 1 -}}

  name: {{ $name }}
  value: {{ default "" $value | quote }}
{{- end -}}

{{- define "common.envvar.configmap" -}}
  {{- $name := index . 0 -}}
  {{- $configMapName := index . 1 -}}
  {{- $configMapKey := index . 2 -}}

  name: {{ $name }}
  valueFrom:
    configMapKeyRef:
      name: {{ $configMapName }}
      key: {{ $configMapKey }}
{{- end -}}

{{- define "common.envvar.secret" -}}
  {{- $name := index . 0 -}}
  {{- $secretName := index . 1 -}}
  {{- $secretKey := index . 2 -}}

  name: {{ $name }}
  valueFrom:
    secretKeyRef:
      name: {{ $secretName }}
      key: {{ $secretKey }}
{{- end -}}

{{- define "common.envvar.metadata" -}}
  {{- $name := index . 0 -}}
  {{- $metadataName := index . 1 -}}

  name: {{ $name }}
  valueFrom:
    fieldRef:
      fieldPath: {{ $metadataName }}
{{- end -}}
