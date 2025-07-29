export interface UniversalConfigCardProps {
  schema?: any
  initialData?: any
}

export interface UniversalConfigCardEmits {
  (e: 'data-change', data: any): void
}