package devandroid.moacir.novoorcamento

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.ActivityNovoLancamentoBinding
import devandroid.moacir.novoorcamento.model.Agenda
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class NovoLancamentoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNovoLancamentoBinding.inflate(layoutInflater) }
    private val db by lazy { AppDatabase.getDatabase(this) }

    private var categorias: List<Categoria> = emptyList()
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()

    // Controle de Edição
    private var isAgenda: Boolean = false
    private var lancamentoParaEditar: Lancamento? = null
    private var agendaParaEditar: Agenda? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)
        inicializarComponentes()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        carregarDadosIniciais()
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            val idRecebido = intent.getIntExtra("LANCAMENTO_ID", -1)
            binding.containerRepetir.visibility = if (isAgenda && idRecebido == -1) View.VISIBLE else View.GONE
            // 1. Busca categorias
            categorias = withContext(Dispatchers.IO) {
                db.orcamentoDao().listarCategorias()
            }

            // 2. Configura Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            binding.spinnerCategorias.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    // Só preenche automaticamente se o campo descrição estiver vazio ou se não for uma edição
                    if (idRecebido == -1 && binding.rbDespesa.isChecked) {
                        val categoriaSelecionada = categorias[position].nome
                        binding.edtDescricao.setText(categoriaSelecionada)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }


            // 3. Verifica se é edição (ID vem do Fragment/Activity chamadora)
            if (idRecebido != -1) {
                binding.txtTituloTela.text = if (isAgenda) "Editar Agenda" else "Editar Lançamento"
                if (isAgenda) {
                    carregarAgendaParaEdicao(idRecebido)
                } else {
                    carregarLancamentoParaEdicao(idRecebido)
                }
            } else {
                binding.txtTituloTela.text = if (isAgenda) "Nova Agenda" else "Novo Lançamento"
                atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
            }
        }
    }

    private suspend fun carregarLancamentoParaEdicao(id: Int) {
        val lancamento = withContext(Dispatchers.IO) {
            db.orcamentoDao().listarLancamentosSemFlow().find { it.id == id }
        }
        lancamento?.let {
            lancamentoParaEditar = it
            preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
        }
    }

    private suspend fun carregarAgendaParaEdicao(id: Int) {
        val agenda = withContext(Dispatchers.IO) {
            db.agendaDao().listarAgendasSemFlow().find { it.id == id }
        }
        agenda?.let {
            agendaParaEditar = it
            preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
        }
    }

    private fun preencherCampos(desc: String, valor: Double, tipo: TipoLancamento, data: Long, catId: Int) {
        with(binding) {
            val valorEmCentavos = (valor * 100).toLong().toString()
            edtValor.setText(valorEmCentavos)
            edtDescricao.setText(desc)

            if (tipo == TipoLancamento.RECEITA) rbReceita.isChecked = true
            else rbDespesa.isChecked = true

            atualizarVisibilidadeCategoria(tipo == TipoLancamento.DESPESA)

            val cal = Calendar.getInstance().apply { timeInMillis = data }
            atualizarTextoData(cal)

            val posicao = categorias.indexOfFirst { it.id == catId }
            if (posicao != -1) spinnerCategorias.setSelection(posicao)
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val cleanValor = binding.edtValor.text.toString().replace(Regex("[^0-9]"), "")
            val repetirStr = binding.edtQtdMeses.text.toString()
            val numRepeticoes = if (isAgenda && repetirStr.isNotEmpty()) repetirStr.toInt() else 1

            if (descricao.isBlank() || cleanValor.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val valorFinal = cleanValor.toDouble() / 100
            val tipo = if (binding.rbReceita.isChecked) TipoLancamento.RECEITA else TipoLancamento.DESPESA
            val catId = if (tipo == TipoLancamento.DESPESA && categorias.isNotEmpty()) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.equals("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) {
                        if (agendaParaEditar != null) {
                            // EDIÇÃO: Apenas um item, ignora repetição
                            val agenda = agendaParaEditar!!.copy(
                                descricao = descricao,
                                valor = valorFinal,
                                data = dataSelecionadaMillis,
                                categoriaID = catId,
                                tipo = tipo
                            )
                            db.agendaDao().upsertAgenda(agenda)
                        } else {
                            // NOVO ITEM: Lógica de Repetição
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = dataSelecionadaMillis
                            }

                            for (i in 0 until numRepeticoes) {
                                val descricaoComParcela = if (numRepeticoes > 1) {
                                    "$descricao (${i + 1}/$numRepeticoes)"
                                } else {
                                    descricao
                                }

                                val novaAgenda = Agenda(
                                    descricao = descricaoComParcela,
                                    valor = valorFinal,
                                    data = cal.timeInMillis, // Usa a data atualizada do calendário
                                    categoriaID = catId,
                                    tipo = tipo
                                )

                                db.agendaDao().upsertAgenda(novaAgenda)

                                // Incrementa 1 mês para a próxima parcela
                                cal.add(Calendar.MONTH, 1)
                            }
                        }
                    } else {
                        // MODO LANÇAMENTO COMUM (Seu código atual...)
                        val lancamento = lancamentoParaEditar?.copy(
                            descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
                        ) ?: Lancamento(
                            descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
                        )
                        db.orcamentoDao().upsertLancamento(lancamento)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NovoLancamentoActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

        }
    }

    private fun configurarCampoData() {
        val calendario = Calendar.getInstance()
        binding.edtData.setOnClickListener {
            DatePickerDialog(this, { _, ano, mes, dia ->
                calendario.set(ano, mes, dia)
                atualizarTextoData(calendario)
            }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun atualizarTextoData(calendario: Calendar) {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        binding.edtData.setText(formato.format(calendario.time))
        dataSelecionadaMillis = calendario.timeInMillis
    }

    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val isDespesa = checkedId == R.id.rbDespesa
            atualizarVisibilidadeCategoria(isDespesa)

            // Se for modo "Novo", ajuda o usuário preenchendo o título
            if (intent.getIntExtra("LANCAMENTO_ID", -1) == -1) {
                if (!isDespesa) {
                    binding.edtDescricao.setText("Receita")
                } else {
                    // Volta para a categoria selecionada no spinner
                    val pos = binding.spinnerCategorias.selectedItemPosition
                    if (pos != -1) binding.edtDescricao.setText(categorias[pos].nome)
                }
            }
        }
    }

    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        binding.lblCategoria.visibility = if (isDespesa) View.VISIBLE else View.GONE
        binding.spinnerCategorias.visibility = if (isDespesa) View.VISIBLE else View.GONE
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")
                    if (cleanString.isNotEmpty()) {
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cleanString.toDouble() / 100)
                        current = formatted
                        binding.edtValor.setText(formatted)
                        binding.edtValor.setSelection(formatted.length)
                    }
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}