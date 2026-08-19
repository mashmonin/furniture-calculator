import { Radio, Typography } from 'antd'

export interface SelectableOption {
  id: number
  label: string
}

interface OptionGroupProps {
  label: string
  options: SelectableOption[]
  selectedId?: number
  onChange: (id: number | undefined) => void
}

export function OptionGroup({ label, options, selectedId, onChange }: OptionGroupProps) {
  if (options.length === 0) {
    return null
  }

  return (
    <div>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <div style={{ marginTop: 4 }}>
        <Radio.Group value={selectedId} onChange={() => {}}>
          {options.map((option) => (
            <Radio.Button
              key={option.id}
              value={option.id}
              onClick={() => onChange(option.id === selectedId ? undefined : option.id)}
            >
              {option.label}
            </Radio.Button>
          ))}
        </Radio.Group>
      </div>
    </div>
  )
}
