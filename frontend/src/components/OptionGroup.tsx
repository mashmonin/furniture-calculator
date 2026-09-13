import { Radio, Select, Typography } from 'antd'

export interface SelectableOption {
  id: number
  label: string
}

interface OptionGroupProps {
  label?: string
  options: SelectableOption[]
  selectedId?: number
  onChange: (id: number | undefined) => void
  variant?: 'buttons' | 'select'
  // Сокращает отображаемое значение выбранного пункта до первого слова + «…» (список в открытом
  // выпадающем меню остаётся полным — сокращается только закрытый контрол). Используется точечно
  // там, где выпадающий список зажат по ширине (см. блок «Фурнитура», change
  // restyle-configurator-per-figma), а не глобально для всех OptionGroup.
  truncateSelectedLabel?: boolean
}

function truncateAfterFirstWord(label: string): string {
  const firstSpaceIndex = label.indexOf(' ')
  return firstSpaceIndex === -1 ? label : `${label.slice(0, firstSpaceIndex)}…`
}

export function OptionGroup({
  label,
  options,
  selectedId,
  onChange,
  variant = 'buttons',
  truncateSelectedLabel = false,
}: OptionGroupProps) {
  if (options.length === 0) {
    return null
  }

  return (
    <div>
      {label && <Typography.Text type="secondary">{label}</Typography.Text>}
      <div style={{ marginTop: 4 }}>
        {variant === 'select' ? (
          <Select
            allowClear
            style={{ width: '100%' }}
            value={selectedId}
            onChange={(id) => onChange(id ?? undefined)}
            onClear={() => onChange(undefined)}
            options={options.map((option) => ({ value: option.id, label: option.label }))}
            labelRender={truncateSelectedLabel ? (props) => truncateAfterFirstWord(String(props.label ?? '')) : undefined}
          />
        ) : (
          // block — растягивает группу на всю ширину и делит её поровну между кнопками (antd добавляет
          // ant-radio-group-block/-wrapper-block: display:flex + flex:1 на каждой кнопке), а не оставляет
          // компактный кластер слева с пустым местом справа (см. change restyle-configurator-per-figma).
          <Radio.Group block value={selectedId} onChange={() => {}}>
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
        )}
      </div>
    </div>
  )
}
