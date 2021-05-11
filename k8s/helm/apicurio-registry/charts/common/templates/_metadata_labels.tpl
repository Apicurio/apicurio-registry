{{- /*
  *NOTE* Values will be quoted. Keys will not.
  common.labelize takes a dict or map and generates labels.

Example template:
  {{- $map := dict "first" "Matt" "last" "Butcher" -}}
  {{- template "common.labelize" $map -}}

Example output:
  first: "Matt"
  last: "Butcher"

*/ -}}
{{- define "common.labelize" -}}
{{- range $k, $v := . }}
{{ $k }}: {{ $v | quote }}
{{- end -}}
{{- end -}}

{{- /*
common.labels.standard prints the standard Helm labels.

The standard labels are frequently used in metadata.
*/ -}}
{{- define "common.labels.standard" -}}
app.kubernetes.io/name: {{ template "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
helm.sh/chart: {{ template "common.chartref" . }}
name: {{ template "common.name" . }}
{{- end -}}


{{- /*
  *NOTE* common.matchlabels.standard prints only needed standard Helm labels for *matching*
*/ -}}
{{- define "common.matchlabels.standard" -}}
app.kubernetes.io/name: {{ template "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ if .Values.nameOverride }}{{ .Values.nameOverride }}{{ else }}{{ template "common.name" . }}{{ end }}
name: {{ if .Values.nameOverride }}{{ .Values.nameOverride }}{{ else }}{{ template "common.name" . }}{{ end }}
{{- end -}}

{{- /*
  *NOTE* common.matchlabels.standard prints only needed standard Helm labels for *matching*
*/ -}}
{{- define "common.matchlabels.shard" -}}
role: {{ if .Values.roleOverride }}{{ .Values.roleOverride }}{{ else }}{{ template "common.name" . }}{{ end }}
{{- end -}}


{{- /*
  *NOTE* common.matchlabels.annote prints only prometheus.io
*/ -}}
{{ define "common.matchlabels.annote" -}}
date: {{ now }}
prometheus.io/scrape: true
prometheus.io/path: /actuator/prometheus
prometheus.io/port: 8091
prometheus.io/scheme: http
{{- end -}}


{{- /*
  *NOTE* common.matchlabels.cj prints only needed standard Helm labels for *matching*
*/ -}}
{{- define "common.matchlabels.cj" -}}
app.kubernetes.io/name: {{ template "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
name: {{ template "common.name" . }}
cron: {{ template "common.name" . }}{{ default "-cj" .suffix }}
{{- end -}}

{{- /*
    kind: PersistentVolume
  *NOTE* common.pvlabels.standard prints standard Helm labels for --> Local Volumes.. From the Same VM..
*/ -}}
{{- define "common.pvlabels.standard" -}}
app.kubernetes.io/name: {{ template "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
helm.sh/chart: {{ template "common.chartref" . }}
type: local
use: {{ template "common.name" . }}
{{- end -}}


{{- /*
    kind: PersistentVolumeClaim
  *NOTE* common.pvclabels.standard prints standard Helm labels for --> Local Volumes.. From the Same VM..
*/ -}}
{{- define "common.pvclabels.standard" -}}
app.kubernetes.io/name: {{ template "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
helm.sh/chart: {{ template "common.chartref" . }}
io.kompose.service: {{ template "common.name" . }}
{{- end -}}
